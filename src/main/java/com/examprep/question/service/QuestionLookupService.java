package com.examprep.question.service;

import com.examprep.catalog.dto.TopicPath;
import com.examprep.catalog.service.CatalogQueryService;
import com.examprep.question.dto.PickCriteria;
import com.examprep.question.dto.QuestionPin;
import com.examprep.question.dto.QuestionRef;
import com.examprep.question.dto.QuestionSummaryDto;
import com.examprep.question.dto.ReviewQuestionView;
import com.examprep.question.dto.ScoringRef;
import com.examprep.question.dto.StudentQuestionView;
import com.examprep.question.dto.StudentTranslation;
import com.examprep.question.entity.Language;
import com.examprep.question.entity.Question;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.mapper.QuestionMapper;
import com.examprep.question.model.QuestionSnapshot;
import com.examprep.question.model.QuestionTranslation;
import com.examprep.question.repository.QuestionRepository;
import com.examprep.user.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only question API for other modules. The test builder, the generator, the paper
 * builder and evaluation depend on this service, never on the question repository or
 * entity directly.
 *
 * <p>Two kinds of reads:
 * <ul>
 *   <li><b>Working copy</b> ({@link #findRefs}, {@link #findSummaries}): the bank as it is
 *       now, for builders and pickers.</li>
 *   <li><b>Pinned</b> ({@link #findStudentViews}, {@link #findScoringRefs},
 *       {@link #findReviewViews}, {@link #findPassages}): the exact versions a test uses,
 *       read from immutable snapshots. Editing the bank never changes these.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionLookupService {

    private final QuestionRepository repository;
    private final QuestionMapper mapper;
    private final CatalogQueryService catalog;
    private final EntityManager entityManager;
    private final QuestionVersionService versions;
    private final UserService users;

    // ------------------------------------------------------------------ working copy

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
        Map<UUID, String> names = users.findNames(questions.stream().map(Question::getReviewerId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<UUID, QuestionSummaryDto> result = new HashMap<>();
        questions.forEach(q -> result.put(q.getId(), mapper.toSummary(q, paths.get(q.getTopicId()), names)));
        return result;
    }

    /** Usable children (published, not archived) of a PARAGRAPH, in creation order (UUIDv7 ids sort by time). */
    public List<UUID> findUsableChildIds(UUID paragraphId) {
        return repository.findUsableChildIds(paragraphId);
    }

    // ------------------------------------------------------------------ pinned versions

    /** Student-safe views of the pinned versions (never contains answers or solutions). */
    public Map<UUID, StudentQuestionView> findStudentViews(Collection<QuestionPin> pins) {
        Map<UUID, StudentQuestionView> out = new HashMap<>();
        snapshots(pins).forEach((id, s) -> out.put(id, new StudentQuestionView(id, s.type(), s.parentId(),
                s.content().text(), s.content().images(), s.content().options(), s.content().matchLeft(),
                s.content().matchRight(), s.language(), studentTranslations(s), s.content().optionsMayShuffle(),
                s.content().numericFormat())));
        return out;
    }

    public Map<UUID, ScoringRef> findScoringRefs(Collection<QuestionPin> pins) {
        Map<UUID, ScoringRef> out = new HashMap<>();
        snapshots(pins).forEach((id, s) -> out.put(id, new ScoringRef(id, s.type(), s.answerKey(), s.difficulty(),
                s.subjectId(), s.chapterId(), s.topicId())));
        return out;
    }

    public Map<UUID, ReviewQuestionView> findReviewViews(Collection<QuestionPin> pins) {
        Map<UUID, ReviewQuestionView> out = new HashMap<>();
        snapshots(pins).forEach((id, s) -> out.put(id, new ReviewQuestionView(id, s.type(), s.parentId(),
                s.content().text(), s.content().images(), s.content().options(), s.content().matchLeft(),
                s.content().matchRight(), s.answerKey(), s.content().solution(), s.language(),
                s.translations().without(s.language()).asMap())));
        return out;
    }

    /** Passages of the pinned paragraph versions, keyed by paragraph id. */
    public Map<UUID, StudentQuestionView.PassageView> findPassages(Map<UUID, Integer> paragraphPins) {
        if (paragraphPins.isEmpty()) {
            return Map.of();
        }
        List<QuestionPin> pins = paragraphPins.entrySet().stream()
                .map(e -> new QuestionPin(e.getKey(), e.getValue())).toList();
        Map<UUID, StudentQuestionView.PassageView> out = new HashMap<>();
        snapshots(pins).forEach((id, s) -> {
            Map<Language, String> translated = new EnumMap<>(Language.class);
            s.translations().without(s.language()).asMap().forEach((lang, t) -> {
                if (t.paragraph() != null) {
                    translated.put(lang, t.paragraph());
                }
            });
            out.put(id, new StudentQuestionView.PassageView(id, s.content().paragraph(), s.content().images(),
                    s.language(), Map.copyOf(translated)));
        });
        return out;
    }

    /**
     * Snapshots for the pins. A pin without a version row (possible only for data written
     * outside the application) falls back to the working copy, with a warning.
     */
    private Map<UUID, QuestionSnapshot> snapshots(Collection<QuestionPin> pins) {
        if (pins.isEmpty()) {
            return Map.of();
        }
        Map<UUID, QuestionSnapshot> found = new HashMap<>(versions.load(pins));
        List<UUID> missing = pins.stream().map(QuestionPin::questionId).filter(id -> !found.containsKey(id))
                .distinct().toList();
        if (!missing.isEmpty()) {
            log.warn("No stored version for {} pinned question(s), using the working copy: {}", missing.size(),
                    missing.stream().limit(5).toList());
            repository.findAllById(missing).forEach(q -> found.put(q.getId(), QuestionSnapshot.of(q)));
        }
        return found;
    }

    private static Map<Language, StudentTranslation> studentTranslations(QuestionSnapshot s) {
        Map<Language, QuestionTranslation> all = s.translations().without(s.language()).asMap();
        if (all.isEmpty()) {
            return Map.of();
        }
        Map<Language, StudentTranslation> out = new EnumMap<>(Language.class);
        all.forEach((lang, t) -> out.put(lang, StudentTranslation.of(t)));
        return Map.copyOf(out);
    }

    // ------------------------------------------------------------------ generator

    /**
     * Picks up to {@code count} random usable (published, not archived), standalone (no
     * paragraph parent) questions.
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
                WHERE q.published_version IS NOT NULL AND q.status <> 'ARCHIVED'
                  AND q.parent_id IS NULL AND q.type <> :paragraph
                """);
        Map<String, Object> params = new HashMap<>();
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
                q.getChapterId(), q.getTopicId(), q.getParentId(), q.getDefaultMarks(), q.getDefaultNegativeMarks(),
                q.getPublishedVersion(), q.getCurrentVersion());
    }
}
