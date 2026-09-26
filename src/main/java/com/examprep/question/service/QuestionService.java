package com.examprep.question.service;

import com.examprep.audit.service.AuditContext;
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
import com.examprep.question.model.QuestionSnapshot;
import com.examprep.question.model.QuestionTranslations;
import com.examprep.question.repository.QuestionRepository;
import com.examprep.question.repository.QuestionSpecifications;
import com.examprep.question.service.QuestionVersionService.VersionInfo;
import com.examprep.question.workflow.QuestionActivityService;
import com.examprep.question.workflow.QuestionActivityService.Kind;
import com.examprep.question.workflow.QuestionWorkflowService;
import com.examprep.question.workflow.StudioDtos.ActivityDto;
import com.examprep.question.workflow.StudioDtos.VersionDetailDto;
import com.examprep.question.workflow.StudioDtos.VersionDto;
import com.examprep.rbac.service.SubjectScopeGuard;
import com.examprep.security.AuthUser;
import com.examprep.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Question bank use cases: create, edit (each save is a new immutable version), roll back,
 * read and search. Workflow transitions live in {@link QuestionWorkflowService}.
 *
 * <p>Integrity rules:
 * <ul>
 *   <li><b>Ownership.</b> Teachers modify only questions they created; holders of
 *       {@code question.update.any} modify any. Subject-scoped staff stay in their subjects.</li>
 *   <li><b>Versions, not freezes.</b> Tests pin the version they were built with, so a
 *       question may be edited freely, even its answer key: students keep being scored
 *       against the version they saw. Changing a key <em>for a taken test</em> is the
 *       explicit answer-key revision flow (phase A6).</li>
 *   <li><b>No lost updates.</b> A save that names a {@code baseVersion} older than the
 *       current one is rejected, so two editors cannot silently overwrite each other.</li>
 *   <li><b>Paragraphs.</b> Children must reference a PARAGRAPH parent in the same
 *       subject. Nesting is one level only.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class QuestionService {

    private static final int MAX_TAGS = 10;
    private static final int MAX_CONCEPTS = 15;

    private final QuestionRepository repository;
    private final CatalogQueryService catalog;
    private final QuestionValidator validator;
    private final QuestionMapper mapper;
    private final SubjectScopeGuard scopes;
    private final QuestionVersionService versions;
    private final QuestionWorkflowService workflow;
    private final QuestionActivityService activity;
    private final UserService users;
    private final Clock clock;

    @Transactional
    public QuestionDto create(QuestionRequest request, AuthUser user) {
        TopicPath path = catalog.resolveTopic(request.topicId());
        scopes.assertCanAuthor(user, path.subjectId());
        Question question = buildNew(request, path);
        repository.save(question);
        int version = versions.record(question, null, user.id(), note(request.changeNote(), "Created"), null);
        workflow.afterEdit(question, user, version, true);
        AuditContext.entity("QUESTION", question.getId());
        return toDto(question, path, user);
    }

    /**
     * Builds and fully validates a new, unsaved DRAFT question with no versions yet. It is
     * shared with bulk import, which saves the whole batch and its versions at once.
     */
    public Question buildNew(QuestionRequest request, TopicPath path) {
        Question question = new Question();
        apply(question, request, path);
        question.setStatus(QuestionStatus.DRAFT);
        return question;
    }

    @Transactional
    public QuestionDto update(UUID id, QuestionRequest request, AuthUser user) {
        Question question = loadForWrite(id, user);
        TopicPath path = catalog.resolveTopic(request.topicId());
        scopes.assertCanAuthor(user, path.subjectId());   // cannot move it into a subject outside the scope
        return save(question, request, path, user, note(request.changeNote(), null), null);
    }

    /** Creates a new version with the content of an older one ("restore v3"), then treats it like an edit. */
    @Transactional
    public QuestionDto restoreVersion(UUID id, int version, String note, AuthUser user) {
        Question question = loadForWrite(id, user);
        QuestionSnapshot old = versions.load(id, version)
                .orElseThrow(() -> NotFoundException.of("Question version", id + " v" + version));
        if (old.equals(QuestionSnapshot.of(question))) {
            throw new BusinessException(ErrorCode.QUESTION_WORKFLOW, "The current version already has this content");
        }
        TopicPath path = catalog.resolveTopic(old.topicId());
        scopes.assertCanAuthor(user, path.subjectId());
        QuestionDto dto = save(question, toRequest(old), path, user,
                note(note, "Restored from v" + version), version);
        activity.log(id, question.getCurrentVersion(), user.id(), Kind.ROLLED_BACK, note, null,
                Map.of("restoredFrom", version));
        AuditContext.action("question.version.restore");
        AuditContext.meta("restoredFrom", version);
        return dto;
    }

    private QuestionDto save(Question question, QuestionRequest request, TopicPath path, AuthUser user, String note,
                             Integer restoredFrom) {
        if (question.getStatus() == QuestionStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.QUESTION_WORKFLOW, "Restore the question before editing it");
        }
        if (request.baseVersion() != null && request.baseVersion() != question.getCurrentVersion()) {
            throw new BusinessException(ErrorCode.QUESTION_VERSION_CONFLICT, "Version " + question.getCurrentVersion()
                    + " was saved while you were editing version " + request.baseVersion()
                    + ". Reload to see it; your unsaved changes stay in this browser.");
        }
        if (question.getType() == QuestionType.PARAGRAPH && request.type() != QuestionType.PARAGRAPH
                && repository.countByParentId(question.getId()) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "Paragraph has child questions; its type cannot change");
        }
        QuestionSnapshot before = QuestionSnapshot.of(question);
        apply(question, request, path);
        if (QuestionSnapshot.of(question).equals(before)) {
            return toDto(question, path, user);   // nothing changed: no new version
        }
        int version = versions.record(question, before, user.id(), note, restoredFrom);
        workflow.afterEdit(question, user, version, false);
        AuditContext.entity("QUESTION", question.getId());
        AuditContext.meta("version", version);
        return toDto(question, path, user);
    }

    /** Soft delete. Tests that already include the question keep working. */
    @Transactional
    public void archive(UUID id, AuthUser user) {
        workflow.archive(id, user);
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public QuestionDto get(UUID id, AuthUser user) {
        Question question = load(id);
        TopicPath path = question.getTopicId() == null ? null
                : catalog.resolveTopics(Set.of(question.getTopicId())).get(question.getTopicId());
        return toDto(question, path, user);
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestionSummaryDto> search(QuestionSearchFilter filter, Pageable pageable, AuthUser user) {
        Page<Question> page = repository.findAll(QuestionSpecifications.matching(filter, user.id(),
                scopes.scope(user).orElse(null), clock.instant()), pageable);
        Map<UUID, TopicPath> paths = catalog.resolveTopics(page.getContent().stream()
                .map(Question::getTopicId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<UUID, String> names = users.findNames(page.getContent().stream().map(Question::getReviewerId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        return PageResponse.of(page, q -> mapper.toSummary(q, paths.get(q.getTopicId()), names));
    }

    @Transactional(readOnly = true)
    public java.util.List<VersionDto> versions(UUID id) {
        Question q = load(id);
        return versions.history(id).stream().map(v -> toVersionDto(v, q)).toList();
    }

    @Transactional(readOnly = true)
    public VersionDetailDto version(UUID id, int version) {
        Question q = load(id);
        VersionInfo info = versions.history(id).stream().filter(v -> v.version() == version).findFirst()
                .orElseThrow(() -> NotFoundException.of("Question version", id + " v" + version));
        QuestionSnapshot snapshot = versions.load(id, version).orElseThrow();
        return new VersionDetailDto(toVersionDto(info, q), snapshot);
    }

    @Transactional(readOnly = true)
    public java.util.List<ActivityDto> timeline(UUID id) {
        load(id);
        return activity.timeline(id);
    }

    @Transactional(readOnly = true)
    public java.util.List<String> subTopics(UUID topicId) {
        return repository.findSubTopics(topicId);
    }

    // ------------------------------------------------------------------ helpers

    private QuestionDto toDto(Question q, TopicPath path, AuthUser user) {
        Set<UUID> people = Stream.of(q.getCreatedBy(), q.getReviewerId(), q.getSubmittedBy())
                .filter(Objects::nonNull).collect(Collectors.toSet());
        QuestionMapper.Extras extras = new QuestionMapper.Extras(users.findNames(people),
                q.getId() == null ? 0 : activity.openComments(q.getId()),
                q.getId() == null ? 0 : repository.countPublishedTestsUsing(q.getId()),
                workflow.allowed(q, user).stream().map(Enum::name).toList(), clock.instant());
        return mapper.toDto(q, path, extras);
    }

    private static VersionDto toVersionDto(VersionInfo v, Question q) {
        return new VersionDto(v.version(), v.changedFields(), v.changeNote(), v.restoredFrom(), v.createdBy(),
                v.createdByName(), v.createdAt(), v.publishedAt(), v.publishedByName(),
                Objects.equals(q.getPublishedVersion(), v.version()), q.getCurrentVersion() == v.version());
    }

    private void apply(Question question, QuestionRequest request, TopicPath path) {
        QuestionType type = request.type();
        AnswerKey key = effectiveKey(request);
        Language language = request.language() != null ? request.language() : Language.EN;
        QuestionTranslations translations = QuestionTranslations.of(request.translations()).without(language);
        validator.validate(type, request.content(), key);
        validator.validateTranslations(type, request.content(), language, translations);
        validateParent(question, request, path);

        question.setType(type);
        question.setDifficulty(request.difficulty() != null ? request.difficulty() : Difficulty.MEDIUM);
        question.setLanguage(language);
        question.setExamId(path.examId());
        question.setSubjectId(path.subjectId());
        question.setChapterId(path.chapterId());
        question.setTopicId(path.topicId());
        question.setSubTopic(blankToNull(request.subTopic()));
        question.setParentId(request.parentId());
        question.setContent(request.content());
        question.setAnswerKey(key);
        question.setTranslations(translations);
        question.setDefaultMarks(request.marks() != null ? request.marks() : type.getDefaultMarks());
        question.setDefaultNegativeMarks(request.negativeMarks() != null
                ? request.negativeMarks() : type.getDefaultNegativeMarks());
        question.setSourceType(request.sourceType());
        question.setSource(blankToNull(request.source()));
        question.setYear(request.year() == null ? null : request.year().shortValue());
        question.setPyqShift(blankToNull(request.pyqShift()));
        question.setExpectedTimeSec(request.expectedTimeSec() == null ? null : request.expectedTimeSec().shortValue());
        question.setCognitiveLevel(request.cognitiveLevel());
        replace(question.getTags(), normalize(request.tags(), MAX_TAGS, "tags"));
        replace(question.getConcepts(), normalize(request.concepts(), MAX_CONCEPTS, "concepts"));
    }

    /** Updates a collection in place only if it changed, so Hibernate does not rewrite equal rows. */
    private static void replace(Set<String> target, Set<String> next) {
        if (!target.equals(next)) {
            target.clear();
            target.addAll(next);
        }
    }

    /** The request a snapshot corresponds to (for restores). */
    private static QuestionRequest toRequest(QuestionSnapshot s) {
        return new QuestionRequest(s.type(), s.difficulty(), s.language(), s.topicId(), s.subTopic(), s.parentId(),
                s.content(), s.answerKey(), s.translations().asMap(), s.marks(), s.negativeMarks(), s.sourceType(),
                s.source(), s.year() == null ? null : s.year().intValue(), s.pyqShift(), s.expectedTimeSec(),
                s.cognitiveLevel(), new HashSet<>(s.tags()), new HashSet<>(s.concepts()), null, null);
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

    private Question load(UUID id) {
        return repository.findById(id).orElseThrow(() -> NotFoundException.of("Question", id));
    }

    private Question loadForWrite(UUID id, AuthUser user) {
        Question question = load(id);
        workflow.requireModify(question, user);
        return question;
    }

    private static Set<String> normalize(Set<String> values, int max, String what) {
        if (values == null) {
            return Set.of();
        }
        Set<String> normalized = values.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.trim().toLowerCase())
                .collect(Collectors.toSet());
        if (normalized.size() > max) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "At most " + max + " " + what + " allowed");
        }
        return normalized;
    }

    private static String note(String given, String fallback) {
        return given == null || given.isBlank() ? fallback : given.trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
