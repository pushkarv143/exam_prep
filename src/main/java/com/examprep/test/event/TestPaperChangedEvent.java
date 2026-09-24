package com.examprep.test.event;

import java.util.UUID;

/**
 * The student-visible paper of a test may have changed, or it just became attemptable
 * (published, unpublished, window opened, archived). The attempt engine listens to this
 * to evict or pre-warm its cached paper.
 *
 * @param warm true when students are about to start (pre-build the paper now, before the rush)
 */
public record TestPaperChangedEvent(UUID testId, boolean warm) {
}
