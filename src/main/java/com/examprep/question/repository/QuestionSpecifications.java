package com.examprep.question.repository;

import com.examprep.common.config.SqlFunctionsContributor;
import com.examprep.question.dto.QuestionSearchFilter;
import com.examprep.question.entity.Question;
import com.examprep.question.entity.QuestionStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Builds the dynamic WHERE clause for question-bank search. */
public final class QuestionSpecifications {

    private QuestionSpecifications() {
    }

    public static Specification<Question> matching(QuestionSearchFilter f, UUID currentUserId) {
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

            if (f.status() != null) {
                p.add(cb.equal(root.get("status"), f.status()));
            } else {
                p.add(cb.notEqual(root.get("status"), QuestionStatus.ARCHIVED));
            }
            if (Boolean.TRUE.equals(f.mine())) {
                p.add(cb.equal(root.get("createdBy"), currentUserId));
            }
            if (f.tag() != null && !f.tag().isBlank()) {
                Join<Question, String> tags = root.join("tags");
                p.add(cb.equal(tags, f.tag().trim().toLowerCase()));
                query.distinct(true);
            }
            if (f.q() != null && !f.q().isBlank()) {
                p.add(cb.like(
                        cb.function(SqlFunctionsContributor.QUESTION_TEXT_LOWER, String.class, root.get("content")),
                        "%" + escapeLike(f.q().trim().toLowerCase()) + "%", '\\'));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static void eq(List<Predicate> predicates, jakarta.persistence.criteria.CriteriaBuilder cb,
                           jakarta.persistence.criteria.Path<?> path, Object value) {
        if (value != null) {
            predicates.add(cb.equal(path, value));
        }
    }

    /** Treat % and _ typed by the user literally. */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
