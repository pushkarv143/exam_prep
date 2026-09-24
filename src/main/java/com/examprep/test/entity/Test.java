package com.examprep.test.entity;

import com.examprep.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A mock test. Structure (sections, questions, marks) is editable only in DRAFT.
 * Totals are denormalised and recomputed by {@code TestBuilderService} on every
 * structural change.
 */
@Getter
@Setter
@Entity
@Table(name = "tests")
public class Test extends BaseEntity {

    /** Null = standalone test (only reachable if free, or by staff). */
    @Column(name = "series_id")
    private UUID seriesId;

    @Column(name = "exam_id", nullable = false)
    private UUID examId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ExamPattern pattern = ExamPattern.CUSTOM;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "total_marks", nullable = false, precision = 8, scale = 2)
    private BigDecimal totalMarks = BigDecimal.ZERO;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TestStatus status = TestStatus.DRAFT;

    /** Free sample test inside a paid series: accessible without enrollment. */
    @Column(name = "is_free", nullable = false)
    private boolean free;

    @Column(name = "shuffle_questions", nullable = false)
    private boolean shuffleQuestions;

    @Column(name = "shuffle_options", nullable = false)
    private boolean shuffleOptions;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 1;

    @Column(name = "show_result_immediately", nullable = false)
    private boolean showResultImmediately = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "ranks_computed_at")
    private Instant ranksComputedAt;
}
