package com.examprep.question.entity;

/**
 * Review workflow of a question's latest revision:
 * <pre>
 * DRAFT ──submit──▶ IN_REVIEW ──approve──▶ APPROVED ──publish──▶ PUBLISHED
 *   ▲                  │                                            │
 *   └──── edit ── CHANGES_REQUESTED ◀── request changes             │ edit = new revision (DRAFT)
 *   ▲                                                               ▼
 *   └───────────────────────────── any state ──archive──▶ ARCHIVED (restore)
 * </pre>
 * Whether a question can be used in tests does <b>not</b> depend on this status alone: a
 * published question that is being revised is DRAFT again but keeps its
 * {@code published_version}, which tests continue to use. See {@code Question#isUsable()}.
 */
public enum QuestionStatus {
    /** Work in progress. */
    DRAFT,
    /** Waiting for (or being checked by) a reviewer. */
    IN_REVIEW,
    /** The reviewer sent it back with comments. */
    CHANGES_REQUESTED,
    /** Approved; a publisher can make this revision the live one. */
    APPROVED,
    /** The latest revision is the live one. */
    PUBLISHED,
    /** Soft-deleted. Hidden from search and pickers; tests that use it keep working. */
    ARCHIVED;

    /** States in which the author may submit the revision for review. */
    public boolean canSubmit() {
        return this == DRAFT || this == CHANGES_REQUESTED;
    }
}
