package com.examprep.test.entity;

import com.examprep.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A question placed in a test section. The marks are the <b>effective</b> values,
 * resolved when the question is added (request > section default > question default),
 * so evaluation never needs to re-derive them.
 */
@Getter
@Setter
@Entity
@Table(name = "test_questions")
public class TestQuestion extends BaseEntity {

    @Column(name = "test_id", nullable = false, updatable = false)
    private UUID testId;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal marks;

    @Column(name = "negative_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal negativeMarks;

    /** Multiple-correct partial scheme (+1 per correct option when no wrong option is chosen). */
    @Column(name = "partial_marking", nullable = false)
    private boolean partialMarking;
}
