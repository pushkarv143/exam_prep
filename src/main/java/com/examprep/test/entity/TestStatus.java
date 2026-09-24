package com.examprep.test.entity;

/**
 * Test lifecycle.
 * <pre>
 *   DRAFT ──publish──▶ PUBLISHED ──(start_at reached)──▶ LIVE ──(end_at reached)──▶ COMPLETED
 *     ▲                    │                               │
 *     └──unpublish (only if no attempts)──────────────────┘          any ──archive──▶ ARCHIVED
 * </pre>
 * {@code TestStatusScheduler} performs the time-driven transitions. Tests without a
 * window stay PUBLISHED and can be attempted any time.
 */
public enum TestStatus {
    DRAFT,
    PUBLISHED,
    LIVE,
    COMPLETED,
    ARCHIVED;

    public boolean isVisibleToStudents() {
        return this == PUBLISHED || this == LIVE || this == COMPLETED;
    }
}
