package com.examprep.question.entity;

public enum QuestionStatus {
    /** Work in progress; cannot be added to tests. */
    DRAFT,
    /** Usable in tests and the auto-generator. */
    ACTIVE,
    /** Soft-deleted. Hidden from search, but existing tests that use it keep working. */
    ARCHIVED
}
