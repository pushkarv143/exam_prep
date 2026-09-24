package com.examprep.result.event;

import java.util.UUID;

public final class ResultEvents {

    private ResultEvents() {
    }

    /** An attempt has been scored (published inside the evaluation transaction). */
    public record ResultEvaluatedEvent(UUID resultId, UUID attemptId, UUID testId, UUID userId) {
    }

    /** Final ranks and percentiles of a test are persisted. The result emails go out after this. */
    public record ResultsFinalizedEvent(UUID testId, int rankedCount) {
    }
}
