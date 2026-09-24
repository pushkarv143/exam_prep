package com.examprep.attempt.service;

import com.examprep.attempt.AttemptProperties;
import com.examprep.attempt.entity.SubmitType;
import com.examprep.attempt.repository.AttemptRepository;
import com.examprep.attempt.store.AttemptRedisStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Force-submits attempts whose time is up.
 *
 * <ul>
 *   <li><b>Primary (every 10 s):</b> the {@code attempts:deadlines} sorted set. An
 *       O(log n) range query returns exactly the due attempts.</li>
 *   <li><b>Fallback (every 2 min):</b> Postgres sweep over
 *       {@code ix_attempts_in_progress_deadline}, in case Redis lost the queue.</li>
 * </ul>
 * It runs on every instance. {@link SubmissionService} makes double processing harmless
 * (lock plus row status). A fixed-window exam ending for 50k students at once is
 * drained in parallel (bounded concurrency on virtual threads) rather than one by one.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoSubmitScheduler {

    private final AttemptRedisStore store;
    private final AttemptRepository repository;
    private final SubmissionService submissions;
    private final AttemptProperties props;
    private final Clock clock;

    @Scheduled(fixedDelayString = "PT10S", initialDelayString = "PT15S")
    public void sweepRedis() {
        Instant cutoff = Instant.now(clock).minus(props.autoSubmitGrace());
        List<UUID> due;
        try {
            due = store.dueForAutoSubmit(cutoff, props.autoSubmitBatch());
        } catch (RuntimeException e) {
            log.warn("Auto-submit Redis sweep skipped: {}", e.getMessage());
            return;
        }
        submitAll(due, "redis");
    }

    @Scheduled(fixedDelayString = "PT2M", initialDelayString = "PT1M")
    public void sweepDatabase() {
        Instant cutoff = Instant.now(clock).minus(props.autoSubmitGrace()).minus(Duration.ofMinutes(1));
        submitAll(repository.findExpiredInProgressIds(cutoff, Limit.of(props.autoSubmitBatch())), "db");
    }

    void submitAll(List<UUID> ids, String source) {
        if (ids.isEmpty()) {
            return;
        }
        AtomicInteger done = new AtomicInteger();
        Semaphore permits = new Semaphore(Math.max(1, props.autoSubmitConcurrency()));
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (UUID id : ids) {
                executor.submit(() -> {
                    permits.acquireUninterruptibly();
                    try {
                        submissions.submit(id, null, SubmitType.AUTO, "Time expired").ifPresent(s -> done.incrementAndGet());
                    } catch (com.examprep.common.exception.NotFoundException e) {
                        store.removeFromDeadlines(id);     // stale queue entry
                    } catch (RuntimeException e) {
                        log.error("Auto-submit of attempt {} failed: {}", id, e.getMessage());
                    } finally {
                        permits.release();
                    }
                });
            }
        }   // close() waits for all tasks
        log.info("Auto-submit ({}): processed {} of {} due attempts", source, done.get(), ids.size());
    }
}
