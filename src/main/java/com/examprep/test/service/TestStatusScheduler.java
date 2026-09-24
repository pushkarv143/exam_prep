package com.examprep.test.service;

import com.examprep.test.event.TestPaperChangedEvent;
import com.examprep.test.event.TestWindowClosedEvent;
import com.examprep.test.repository.TestStatusTransitions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Moves windowed tests PUBLISHED → LIVE → COMPLETED every 30 seconds. It runs on every
 * instance. The UPDATE ... RETURNING statements make each transition (and its event)
 * happen exactly once cluster-wide.
 *
 * <p>Status is advisory for display. Attempt start/submit checks always compare the
 * window against the clock directly, so a scheduler delay never lets anyone in late.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestStatusScheduler {

    private final TestStatusTransitions transitions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT20S")
    @Transactional
    public void advance() {
        Instant now = Instant.now(clock);
        List<UUID> opened = transitions.activateDue(now);
        List<UUID> closed = transitions.completeDue(now);
        if (!opened.isEmpty() || !closed.isEmpty()) {
            log.info("Test windows: {} opened, {} closed", opened.size(), closed.size());
        }
        // Listeners should be @TransactionalEventListener(AFTER_COMMIT) so they see the committed status.
        closed.forEach(id -> events.publishEvent(new TestWindowClosedEvent(id)));
        // Pre-build the paper as the window opens, before thousands of students hit "Start".
        opened.forEach(id -> events.publishEvent(new TestPaperChangedEvent(id, true)));
    }
}
