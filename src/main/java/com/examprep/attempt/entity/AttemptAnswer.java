package com.examprep.attempt.entity;

import com.examprep.attempt.model.StudentAnswer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Persisted answer (hash-partitioned table). Rows are written in bulk by
 * {@code SubmissionService} via JDBC batch upsert, not by JPA. The entity exists for
 * reads (evaluation, solution review) and for evaluation writing back the outcome.
 */
@Getter
@Setter
@Entity
@Table(name = "attempt_answers")
@IdClass(AttemptAnswer.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttemptAnswer {

    @Id
    @Column(name = "attempt_id")
    private UUID attemptId;

    @Id
    @Column(name = "question_id")
    private UUID questionId;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private StudentAnswer answer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnswerState state;

    @Column(name = "time_spent_seconds", nullable = false)
    private int timeSpentSeconds;

    @Column(name = "visit_count", nullable = false)
    private int visitCount;

    /** Set by evaluation: CORRECT / INCORRECT / PARTIAL / UNATTEMPTED. */
    private String outcome;

    @Column(name = "marks_awarded", precision = 6, scale = 2)
    private BigDecimal marksAwarded;

    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;

    @Getter
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Key implements Serializable {
        private UUID attemptId;
        private UUID questionId;
    }
}
