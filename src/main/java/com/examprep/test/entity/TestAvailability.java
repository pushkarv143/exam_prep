package com.examprep.test.entity;

import java.time.Instant;

/** Whether a student can start the test <em>right now</em>, derived from status and window. */
public enum TestAvailability {
    NOT_PUBLISHED,
    UPCOMING,
    OPEN,
    CLOSED;

    public static TestAvailability of(Test test, Instant now) {
        return switch (test.getStatus()) {
            case DRAFT, ARCHIVED -> NOT_PUBLISHED;
            case COMPLETED -> CLOSED;
            case PUBLISHED, LIVE -> {
                if (test.getStartAt() != null && now.isBefore(test.getStartAt())) {
                    yield UPCOMING;
                }
                if (test.getEndAt() != null && !now.isBefore(test.getEndAt())) {
                    yield CLOSED;
                }
                yield OPEN;
            }
        };
    }
}
