package com.examprep.question.repository;

import com.examprep.common.config.SqlFunctionsContributor;
import com.examprep.question.dto.QuestionSearchFilter;
import com.examprep.question.entity.Question;
import com.examprep.question.entity.QuestionStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Builds the dynamic WHERE clause for question-bank search and the review queues. */
public final class QuestionSpecifications {

    private QuestionSpecifications() {
    }

    /**
     * @param allowedSubjects when non-null, only these subjects are visible (subject-scoped
     *                        teachers); an empty set matches nothing
     */
    public static Specification<Question> matching(QuestionSearchFilter f, UUID currentUserId,
                                                   Set<UUID> allowedSubjects, Instant now) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            eq(p, cb, root.get("examId"), f.examId());
            eq(p, cb, root.get("subjectId"), f.subjectId());
            eq(p, cb, root.get("chapterId"), f.chapterId());
            eq(p, cb, root.get("topicId"), f.topicId());
            eq(p, cb, root.get("parentId"), f.parentId());
            eq(p, cb, root.get("type"), f.type());
            eq(p, cb, root.get("difficulty"), f.difficulty());
            eq(p, cb, root.get("language"), f.language());
            eq(p, cb, root.get("sourceType"), f.sourceType());
            eq(p, cb, root.get("cognitiveLevel"), f.cognitiveLevel());
            eq(p, cb, root.get("year"), f.year() == null ? null : f.year().shortValue());
            if (allowedSubjects != null) {
                p.add(allowedSubjects.isEmpty() ? cb.disjunction() : root.get("subjectId").in(allowedSubjects));
            }

            boolean reviewQueue = f.reviewer() != null && !f.reviewer().isBlank() || Boolean.TRUE.equals(f.overdue());
            if (f.status() != null) {
                p.add(cb.equal(root.get("status"), f.status()));
            } else if (reviewQueue) {
                p.add(cb.equal(root.get("status"), QuestionStatus.IN_REVIEW));
            } else {
                p.add(cb.notEqual(root.get("status"), QuestionStatus.ARCHIVED));
            }
            if (Boolean.TRUE.equals(f.live())) {
                p.add(cb.isNotNull(root.get("publishedVersion")));
                p.add(cb.notEqual(root.get("status"), QuestionStatus.ARCHIVED));
            }
            if (f.reviewer() != null && !f.reviewer().isBlank()) {
                String r = f.reviewer().trim();
                if (r.equalsIgnoreCase("none")) {
                    p.add(cb.isNull(root.get("reviewerId")));
                } else {
                    p.add(cb.equal(root.get("reviewerId"), r.equalsIgnoreCase("me") ? currentUserId : parse(r)));
                }
            }
            if (Boolean.TRUE.equals(f.overdue())) {
                p.add(cb.lessThan(root.get("reviewDueAt"), now));
            }
            if (Boolean.TRUE.equals(f.mine())) {
                p.add(cb.equal(root.get("createdBy"), currentUserId));
            }
            if (f.tag() != null && !f.tag().isBlank()) {
                Join<Question, String> tags = root.join("tags");
                p.add(cb.equal(tags, f.tag().trim().toLowerCase()));
                query.distinct(true);
            }
            if (f.concept() != null && !f.concept().isBlank()) {
                Join<Question, String> concepts = root.join("concepts");
                p.add(cb.equal(concepts, f.concept().trim().toLowerCase()));
                query.distinct(true);
            }
            if (f.translated() != null) {
                p.add(cb.isTrue(cb.function(SqlFunctionsContributor.JSONB_HAS_KEY, Boolean.class,
                        root.get("translations"), cb.literal(f.translated().name()))));
            }
            if (f.q() != null && !f.q().isBlank()) {
                p.add(cb.like(
                        cb.function(SqlFunctionsContributor.QUESTION_TEXT_LOWER, String.class, root.get("content")),
                        "%" + escapeLike(f.q().trim().toLowerCase()) + "%", '\\'));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return new UUID(0, 0);   // matches nothing
        }
    }

    private static void eq(List<Predicate> predicates, CriteriaBuilder cb, Path<?> path, Object value) {
        if (value != null) {
            predicates.add(cb.equal(path, value));
        }
    }

    /** Treat % and _ typed by the user literally. */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
