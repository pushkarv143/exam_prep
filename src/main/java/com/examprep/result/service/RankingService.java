package com.examprep.result.service;

import com.examprep.approval.ApprovalSpec;
import com.examprep.approval.MakerChecker;
import com.examprep.common.redis.RedisLock;
import com.examprep.result.EvaluationProperties;
import com.examprep.result.event.ResultEvents.ResultsFinalizedEvent;
import com.examprep.result.repository.ResultRepository;
import com.examprep.test.service.TestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Final ranks and percentiles.
 *
 * <p><b>Scheduled tests:</b> once the window has closed, the auto-submit grace has passed and
 * every attempt is evaluated, ranks are computed in one SQL statement and frozen. If
 * stragglers keep a test unfinished beyond {@code finalizeForceAfter}, it is finalised
 * anyway; late results then show a live rank.
 *
 * <p><b>Always-open tests</b> have live ranks only, unless an admin triggers
 * {@link #finalizeRanks} (e.g. at the end of a campaign).
 */
@Slf4j
@Service
public class RankingService {

    private final ResultRepository results;
    private final LeaderboardService leaderboard;
    private final TestService tests;
    private final RedisLock lock;
    private final EvaluationProperties props;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final MakerChecker makerChecker;

    public RankingService(ResultRepository results, LeaderboardService leaderboard, TestService tests, RedisLock lock,
                          EvaluationProperties props, ApplicationEventPublisher events, PlatformTransactionManager tm,
                          Clock clock, MakerChecker makerChecker) {
        this.makerChecker = makerChecker;
        this.results = results;
        this.leaderboard = leaderboard;
        this.tests = tests;
        this.lock = lock;
        this.props = props;
        this.events = events;
        this.tx = new TransactionTemplate(tm);
        this.clock = clock;
    }

    /** @return number of ranked results, or -1 if another instance is finalising this test right now */
    public int finalizeRanks(UUID testId) {
        // Admin-triggered runs may need a second person; scheduled runs (no user) never wait.
        makerChecker.guard(ApprovalSpec.of("result.finalize", "TEST", testId,
                "Finalize ranks for \"" + tests.title(testId) + "\"", Map.of("testId", testId)));
        Optional<RedisLock.Handle> handle = lock.tryAcquire("lock:finalize:" + testId, Duration.ofMinutes(5));
        if (handle.isEmpty()) {
            return -1;
        }
        try (RedisLock.Handle ignored = handle.get()) {
            Instant now = Instant.now(clock);
            Integer ranked = tx.execute(s -> {
                int n = results.finalizeRanks(testId, now);
                tests.markRanksComputed(testId, now);
                events.publishEvent(new ResultsFinalizedEvent(testId, n));
                return n;
            });
            leaderboard.rebuild(testId);   // realign the live set with the final data
            log.info("Final ranks computed for test {}: {} candidates", testId, ranked);
            return ranked == null ? 0 : ranked;
        }
    }

    @Scheduled(fixedDelayString = "PT60S", initialDelayString = "PT45S")
    public void finalizeClosedTests() {
        Instant now = Instant.now(clock);
        for (UUID testId : results.testsAwaitingFinalRanks(now.minus(props.finalizeDelay()))) {
            long unfinished = results.countUnfinishedAttempts(testId);
            boolean overdue = results.testsAwaitingFinalRanks(now.minus(props.finalizeForceAfter())).contains(testId);
            if (unfinished == 0 || overdue) {
                if (unfinished > 0) {
                    log.warn("Finalising test {} with {} unfinished attempts (overdue)", testId, unfinished);
                }
                try {
                    finalizeRanks(testId);
                } catch (RuntimeException e) {
                    log.error("Rank finalisation failed for test {}: {}", testId, e.getMessage());
                }
            }
        }
    }
}
