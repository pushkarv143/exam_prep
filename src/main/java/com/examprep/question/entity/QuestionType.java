package com.examprep.question.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

/**
 * Supported question types, each with the default marking scheme used when a question
 * is created without explicit marks. Tests can override marks per question.
 */
@Getter
@RequiredArgsConstructor
public enum QuestionType {

    /** One correct option. JEE Main / NEET: +4 / -1. */
    SINGLE_CORRECT(new BigDecimal("4"), new BigDecimal("1")),
    /** One or more correct options, with partial marking. JEE Advanced style: +4 / -2. */
    MULTIPLE_CORRECT(new BigDecimal("4"), new BigDecimal("2")),
    /** Integer or decimal answer, optionally with a tolerance. No negative marking by default. */
    NUMERICAL(new BigDecimal("4"), BigDecimal.ZERO),
    /** Match column I to column II. */
    MATCH(new BigDecimal("4"), new BigDecimal("1")),
    /** Container for a shared passage. Its child questions (parentId) carry the answers. */
    PARAGRAPH(BigDecimal.ZERO, BigDecimal.ZERO);

    private final BigDecimal defaultMarks;
    private final BigDecimal defaultNegativeMarks;

    /** Whether a student can answer this question directly. PARAGRAPH is only a container. */
    public boolean isAnswerable() {
        return this != PARAGRAPH;
    }

    public boolean hasOptions() {
        return this == SINGLE_CORRECT || this == MULTIPLE_CORRECT;
    }
}
