package com.examprep.test.entity;

import com.examprep.common.entity.BaseEntity;
import com.examprep.question.entity.QuestionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** A tab in the exam UI (e.g. "Physics - Section A") with its own marking rules. */
@Getter
@Setter
@Entity
@Table(name = "test_sections")
public class TestSection extends BaseEntity {

    @Column(name = "test_id", nullable = false, updatable = false)
    private UUID testId;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String instructions;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** Marks applied to questions added without an explicit override. */
    @Column(name = "default_marks", precision = 6, scale = 2)
    private BigDecimal defaultMarks;

    @Column(name = "default_negative_marks", precision = 6, scale = 2)
    private BigDecimal defaultNegativeMarks;

    /**
     * "Attempt any N". NTA rule: if more than N are answered, only the first N answered
     * (in question order) are evaluated. The UI should also block answering beyond N.
     */
    @Column(name = "max_questions_to_attempt")
    private Integer maxQuestionsToAttempt;

    /** If set, only questions of this type may be added. */
    @Enumerated(EnumType.STRING)
    @Column(name = "question_type")
    private QuestionType questionType;

    /** Expected number of questions (from the pattern); used by validation and the generator. */
    @Column(name = "target_count")
    private Integer targetCount;
}
