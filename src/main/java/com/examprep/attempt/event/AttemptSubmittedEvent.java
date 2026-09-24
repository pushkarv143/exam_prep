package com.examprep.attempt.event;

import com.examprep.attempt.entity.SubmitType;

import java.util.UUID;

/**
 * Published inside the submit transaction. Listeners must use
 * {@code @TransactionalEventListener(AFTER_COMMIT)}. Phase 5 turns this into a Redis
 * Stream message for background evaluation.
 */
public record AttemptSubmittedEvent(UUID attemptId, UUID testId, UUID userId, SubmitType submitType) {
}
