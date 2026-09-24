package com.examprep.attempt.entity;

public enum AttemptStatus {
    /** Running. Its answers live in Redis. */
    IN_PROGRESS,
    /** Answers flushed to Postgres, waiting for evaluation (Phase 5). */
    SUBMITTED,
    /** Scored and ranked. */
    EVALUATED,
    /** Voided by an admin (e.g. malpractice). Does not count towards max attempts. */
    CANCELLED
}
