package com.examprep.question.service;

import com.examprep.catalog.dto.TopicPath;
import com.examprep.catalog.service.CatalogQueryService;
import com.examprep.common.api.PageResponse;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.exception.NotFoundException;
import com.examprep.question.dto.QuestionDto;
import com.examprep.question.dto.QuestionRequest;
import com.examprep.question.dto.QuestionSearchFilter;
import com.examprep.question.dto.QuestionSummaryDto;
import com.examprep.question.entity.Difficulty;
import com.examprep.question.entity.Language;
import com.examprep.question.entity.Question;
import com.examprep.question.entity.QuestionStatus;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.mapper.QuestionMapper;
import com.examprep.question.model.AnswerKey;
import com.examprep.question.repository.QuestionRepository;
import com.examprep.question.repository.QuestionSpecifications;
import com.examprep.security.AuthUser;
import com.examprep.user.entity.RoleName;
import com.examprep.question.event.QuestionContentChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Question bank use cases.
 *
 * <p>Integrity rules:
 * <ul>
 *   <li><b>Ownership.</b> Teachers modify only questions they created. Admins modify any.</li>
 *   <li><b>Answer-key freeze.</b> Once a question is in a published test, its type and
 *       answer key are frozen, because students have attempted it against that key.
 *       Wording, solution, tags and difficulty can still be fixed. To change the answer,
 *       create a new question. Re-evaluating an existing test after a key change is an
 *       explicit admin action (results module).</li>
 *   <li><b>Paragraphs.</b> Children must reference a PARAGRAPH parent in the same
 *       subject. Nesting is one level only.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class QuestionService {

    private static final int MAX_TAGS = 10;

    private final QuestionRepository repository;
    private final CatalogQueryService catalog;
    private final QuestionValidator validator;
    private final QuestionMapper mapper;
    private final ApplicationEventPublisher events;

    @Transactional
    public QuestionDto create(QuestionRequest request) {
        TopicPath path = catalog.resolveTopic(request.topicId());
        Question question = buildNew(request, path);
        repository.save(question);
        return mapper.toDto(question, path, false);
    }

    /**
     * Builds and fully validates a new, unsaved question. It is shared with bulk import,
     * which saves the whole batch at once.
     */
    public Question buildNew(QuestionRequest request, TopicPath path) {
        Question question = new Question();
        apply(question, request, path);
        return question;
    }

    @Transactional
    public QuestionDto update(UUID id, QuestionRequest request, AuthUser user) {
        Question question = loadForWrite(id, user);
        TopicPath path = catalog.resolveTopic(request.topicId());
        boolean used = repository.isUsedInPublishedTest(id);

        if (used && (question.getType() != request.type()
                || !Objects.equals(question.getAnswerKey(), effectiveKey(request)))) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "This question is part of a published test, so its type and answer key are frozen. "
                            + "Create a new question instead.");
        }
        if (question.getType() == QuestionType.PARAGRAPH && request.type() != QuestionType.PARAGRAPH
                && repository.countByParentId(id) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "Paragraph has child questions; its type cannot change");
        }
        apply(question, request, path);
        events.publishEvent(new QuestionContentChangedEvent(id));
        return mapper.toDto(question, path, used);
    }

    /** Soft delete. Tests that already include the question keep working. */
    @Transactional
    public void archive(UUID id, AuthUser user) {
        loadForWrite(id, user).setStatus(QuestionStatus.ARCHIVED);
    }

    @Transactional(readOnly = true)
    public QuestionDto get(UUID id) {
        Question question = repository.findById(id).orElseThrow(() -> NotFoundException.of("Question", id));
        TopicPath path = question.getTopicId() == null ? null
                : catalog.resolveTopics(Set.of(question.getTopicId())).get(question.getTopicId());
        return mapper.toDto(question, path, repository.isUsedInPublishedTest(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestionSummaryDto> search(QuestionSearchFilter filter, Pageable pageable, AuthUser user) {
        Page<Question> page = repository.findAll(QuestionSpecifications.matching(filter, user.id()), pageable);
        Map<UUID, TopicPath> paths = catalog.resolveTopics(page.getContent().stream()
                .map(Question::getTopicId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return PageResponse.of(page, q -> mapper.toSummary(q, paths.get(q.getTopicId())));
    }

    // ------------------------------------------------------------------ helpers

    private void apply(Question question, QuestionRequest request, TopicPath path) {
        QuestionType type = request.type();
        AnswerKey key = effectiveKey(request);
        validator.validate(type, request.content(), key);
        validateParent(question, request, path);

        question.setType(type);
        question.setDifficulty(request.difficulty() != null ? request.difficulty() : Difficulty.MEDIUM);
        question.setLanguage(request.language() != null ? request.language() : Language.EN);
        question.setExamId(path.examId());
        question.setSubjectId(path.subjectId());
        question.setChapterId(path.chapterId());
        question.setTopicId(path.topicId());
        question.setParentId(request.parentId());
        question.setContent(request.content());
        question.setAnswerKey(key);
        question.setDefaultMarks(request.marks() != null ? request.marks() : type.getDefaultMarks());
        question.setDefaultNegativeMarks(request.negativeMarks() != null
                ? request.negativeMarks() : type.getDefaultNegativeMarks());
        question.setStatus(request.status() != null ? request.status() : QuestionStatus.ACTIVE);
        question.setSource(request.source());
        question.setYear(request.year() == null ? null : request.year().shortValue());
        question.getTags().clear();
        question.getTags().addAll(normalizeTags(request.tags()));
    }

    /** PARAGRAPH containers have no answer; any key sent for them is discarded. */
    private static AnswerKey effectiveKey(QuestionRequest request) {
        return request.type().isAnswerable() ? request.answerKey() : AnswerKey.EMPTY;
    }

    private void validateParent(Question question, QuestionRequest request, TopicPath path) {
        if (request.parentId() == null) {
            return;
        }
        if (request.type() == QuestionType.PARAGRAPH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Paragraphs cannot be nested");
        }
        if (request.parentId().equals(question.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A question cannot be its own parent");
        }
        Question parent = repository.findById(request.parentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                        "Unknown parent question: " + request.parentId()));
        if (parent.getType() != QuestionType.PARAGRAPH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Parent must be a PARAGRAPH question");
        }
        if (!parent.getSubjectId().equals(path.subjectId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Parent paragraph belongs to another subject");
        }
    }

    private Question loadForWrite(UUID id, AuthUser user) {
        Question question = repository.findById(id).orElseThrow(() -> NotFoundException.of("Question", id));
        if (!user.hasRole(RoleName.ADMIN) && !user.id().equals(question.getCreatedBy())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Teachers can modify only their own questions");
        }
        return question;
    }

    private static Set<String> normalizeTags(Set<String> tags) {
        if (tags == null) {
            return Set.of();
        }
        Set<String> normalized = tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.trim().toLowerCase())
                .collect(Collectors.toSet());
        if (normalized.size() > MAX_TAGS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "At most " + MAX_TAGS + " tags allowed");
        }
        return normalized;
    }
}
