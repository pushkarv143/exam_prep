package com.examprep.attempt.entity;

/**
 * NTA question-palette states. The server <em>derives</em> the state from (answer present,
 * marked for review), so a client can never store an inconsistent combination.
 * <pre>
 *                      marked = false        marked = true
 *   no answer          NOT_ANSWERED          MARKED_FOR_REVIEW
 *   has answer         ANSWERED              ANSWERED_AND_MARKED   (evaluated, per NTA rules)
 * </pre>
 * NOT_VISITED is the implicit state of a question the student never opened (no row).
 */
public enum AnswerState {
    NOT_VISITED,
    NOT_ANSWERED,
    ANSWERED,
    MARKED_FOR_REVIEW,
    ANSWERED_AND_MARKED;

    public static AnswerState of(boolean hasAnswer, boolean markedForReview) {
        if (hasAnswer) {
            return markedForReview ? ANSWERED_AND_MARKED : ANSWERED;
        }
        return markedForReview ? MARKED_FOR_REVIEW : NOT_ANSWERED;
    }

    public boolean hasAnswer() {
        return this == ANSWERED || this == ANSWERED_AND_MARKED;
    }

    public boolean isMarked() {
        return this == MARKED_FOR_REVIEW || this == ANSWERED_AND_MARKED;
    }
}
