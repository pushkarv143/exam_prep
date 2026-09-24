package com.examprep.test.event;

import java.util.UUID;

/**
 * Published once when a scheduled test's window closes (status becomes COMPLETED).
 * The result module (Phase 5) listens to it to compute final ranks and percentiles.
 */
public record TestWindowClosedEvent(UUID testId) {
}
