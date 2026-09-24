package com.examprep.question.service;

import com.examprep.catalog.dto.TopicPath;
import com.examprep.catalog.service.CatalogQueryService;
import com.examprep.question.dto.PickCriteria;
import com.examprep.question.dto.QuestionRef;
import com.examprep.question.dto.QuestionSummaryDto;
import com.examprep.question.dto.ReviewQuestionView;
import com.examprep.question.dto.ScoringRef;
import com.examprep.question.dto.StudentQuestionView;
import com.examprep.question.entity.Question;
import com.examprep.question.entity.QuestionStatus;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.mapper.QuestionMapper;
import com.examprep.question.repository.QuestionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only question API for other modules. The test builder, the generator and (in
 * Phase 4) the paper builder depend on this service, never on the question
 * repository or entity directly.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionLookupService {

    private final QuestionRepository repository;
    private final QuestionMapper mapper;
    private final CatalogQueryService catalog;
    private final EntityManager entityManager;

    public Map<UUID, QuestionRef> findRefs(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(ids).stream()
                .map(QuestionLookupService::toRef)
                .collect(Collectors.toMap(QuestionRef::id, Function.identity()));
    }

    public Map<UUID, QuestionSummaryDto> findSummaries(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Question> questions = repository.findAllById(ids);
        Map<UUID, TopicPath> paths = catalog.resolveTopics(questions.stream()
                .map(Question::getTopicId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<UUID, QuestionSummaryDto> result = new HashMap<>();
        questions.forEach(q -> result.put(q.getId(), mapper.toSummary(q, paths.get(q.getTopicId()))));
        return result;
    }

    /** Student-safe views for building a test paper (never contains answers or solutions). */
    public Map<UUID, StudentQuestionView> findStudentViews(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(ids).stream()
                .collect(Collectors.toMap(Question::getId, q -> new StudentQuestionView(q.getId(), q.getType(),
                        q.getParentId(), q.getContent().text(), q.getContent().images(), q.getContent().options(),
                        q.getContent().matchLeft(), q.getContent().matchRight())));
    }

    public Map<UUID, ScoringRef> findScoringRefs(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(ids).stream()
                .collect(Collectors.toMap(Question::getId, q -> new ScoringRef(q.getId(), q.getType(),
                        q.getAnswerKey(), q.getDifficulty(), q.getSubjectId(), q.getChapterId(), q.getTopicId())));
    }

    public Map<UUID, ReviewQuestionView> findReviewViews(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(ids).stream()
                .collect(Collectors.toMap(Question::getId, q -> new ReviewQuestionView(q.getId(), q.getType(),
                        q.getParentId(), q.getContent().text(), q.getContent().images(), q.getContent().options(),
                        q.getContent().matchLeft(), q.getContent().matchRight(), q.getAnswerKey(),
                        q.getContent().solution())));
    }

    public Map<UUID, StudentQuestionView.PassageView> findPassages(Collection<UUID> paragraphIds) {
        if (paragraphIds.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(paragraphIds).stream()
                .collect(Collectors.toMap(Question::getId, q -> new StudentQuestionView.PassageView(q.getId(),
                        q.getContent().paragraph(), q.getContent().images())));
    }

    /** ACTIVE children of a PARAGRAPH, in creation order (UUIDv7 ids sort by time). */
    public List<UUID> findActiveChildIds(UUID paragraphId) {
        return repository.findActiveChildIds(paragraphId);
    }

    /**
     * Picks up to {@code count} random ACTIVE, standalone (no paragraph parent) questions.
     *
     * <p>{@code ORDER BY random()} is fine at question-bank scale (tens of thousands of
     * rows after the filters are applied, which use {@code ix_questions_topic_difficulty}
     * / {@code ix_questions_subject}). At millions of rows, switch to TABLESAMPLE or a
     * precomputed random key.
     */
    @SuppressWarnings("unchecked")
    public List<UUID> pickRandom(PickCriteria c, int count, Collection<UUID> excludeIds) {
        if (count <= 0) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT q.id FROM questions q
                WHERE q.status = :status AND q.parent_id IS NULL AND q.type <> :paragraph
                """);
        Map<String, Object> params = new HashMap<>();
        params.put("status", QuestionStatus.ACTIVE.name());
        params.put("paragraph", QuestionType.PARAGRAPH.name());

        if (c.subjectId() != null) {
            sql.append(" AND q.subject_id = :subjectId");
            params.put("subjectId", c.subjectId());
        }
        if (c.chapterIds() != null && !c.chapterIds().isEmpty()) {
            sql.append(" AND q.chapter_id IN (:chapterIds)");
            params.put("chapterIds", c.chapterIds());
        }
        if (c.topicIds() != null && !c.topicIds().isEmpty()) {
            sql.append(" AND q.topic_id IN (:topicIds)");
            params.put("topicIds", c.topicIds());
        }
        if (c.difficulty() != null) {
            sql.append(" AND q.difficulty = :difficulty");
            params.put("difficulty", c.difficulty().name());
        }
        if (c.types() != null && !c.types().isEmpty()) {
            sql.append(" AND q.type IN (:types)");
            params.put("types", c.types().stream().map(Enum::name).toList());
        }
        if (c.language() != null) {
            sql.append(" AND q.language = :language");
            params.put("language", c.language().name());
        }
        if (excludeIds != null && !excludeIds.isEmpty()) {
            sql.append(" AND q.id NOT IN (:excludeIds)");
            params.put("excludeIds", excludeIds);
        }
        if (c.excludeUsedInPublishedTests()) {
            sql.append("""
                     AND NOT EXISTS (SELECT 1 FROM test_questions tq JOIN tests t ON t.id = tq.test_id
                                     WHERE tq.question_id = q.id AND t.status <> 'DRAFT')
                    """);
        }
        sql.append(" ORDER BY random() LIMIT :limit");
        params.put("limit", count);

        Query query = entityManager.createNativeQuery(sql.toString(), UUID.class);
        params.forEach(query::setParameter);
        return new ArrayList<>((List<UUID>) query.getResultList());
    }

    private static QuestionRef toRef(Question q) {
        return new QuestionRef(q.getId(), q.getType(), q.getDifficulty(), q.getStatus(), q.getSubjectId(),
                q.getChapterId(), q.getTopicId(), q.getParentId(), q.getDefaultMarks(), q.getDefaultNegativeMarks());
    }
}
