package com.examprep.question.model;

/**
 * Partial marking for MULTIPLE_CORRECT questions, used when the test enables partial
 * marking for the question. Choosing any wrong option always scores the negative marks.
 */
public enum PartialRule {
    /** marks / 4 per correct option chosen (JEE Advanced: +3, +2, +1 for a 4-mark question). */
    JEE_ADVANCED,
    /** marks × chosen / number of correct options. */
    PROPORTIONAL,
    /** All or nothing, even if the test enables partial marking. */
    NONE
}
