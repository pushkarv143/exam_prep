package com.examprep.result.service;

import com.examprep.attempt.entity.AttemptStatus;
import com.examprep.attempt.service.AttemptEvaluationAccess;
import com.examprep.attempt.service.AttemptEvaluationAccess.AnswerRow;
import com.examprep.attempt.service.AttemptEvaluationAccess.AttemptSnapshot;
import com.examprep.attempt.service.AttemptEvaluationAccess.OutcomeRow;
import com.examprep.common.redis.RedisLock;
import com.examprep.result.entity.Result;
import com.examprep.result.event.ResultEvents.ResultEvaluatedEvent;
import com.examprep.result.repository.ResultRepository;
import com.examprep.result.scoring.ScoringEngine;
import com.examprep.result.scoring.ScoringEngine.Evaluation;
import com.examprep.result.scoring.ScoringEngine.Response;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Scores one submitted attempt, idempotently and safely under concurrency.
 * <ol>
 *   <li>Redis lock {@code lock:evaluate:{id}}: another worker already on it means skip.</li>
 *   <li>Row lock plus status check: only a SUBMITTED attempt is scored, unless {@code force}
 *       re-evaluates an EVALUATED one. Redelivered stream messages are therefore harmless.</li>
 *   <li>One transaction: per-question outcomes, the results row (upsert by attempt), and
 *       attempt → EVALUATED.</li>
 *   <li>After commit: live leaderboard update (first attempts only).</li>
 * </ol>
 */
@Slf4j
@Service
public class EvaluationService {

    private final AttemptEvaluationAccess attempts;
    private final EvaluationSpecCache specs;
    private final ResultRepository results;
    private final LeaderboardService leaderboard;
    private final RedisLock lock;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final MeterRegistry meters;
    private final Timer timer;

    public EvaluationService(AttemptEvaluationAccess attempts, EvaluationSpecCache specs, ResultRepository results,
                             LeaderboardService leaderboard, RedisLock lock, PlatformTransactionManager tm,
                             ApplicationEventPublisher events, Clock clock, MeterRegistry meters) {
        this.attempts = attempts;
        this.specs = specs;
        this.results = results;
        this.leaderboard = leaderboard;
        this.lock = lock;
        this.tx = new TransactionTemplate(tm);
        this.events = events;
        this.clock = clock;
        this.meters = meters;
        this.timer = meters.timer("examprep.evaluation.duration");
    }

    /** @return true if this call scored the attempt (false: already done, busy elsewhere, or not submitted) */
    public boolean evaluate(UUID attemptId, boolean force) {
        Optional<RedisLock.Handle> handle;
        try {
            handle = lock.tryAcquire("lock:evaluate:" + attemptId, Duration.ofSeconds(60));
        } catch (RuntimeException e) {
            log.warn("Evaluation lock unavailable ({}); relying on the row lock", e.getMessage());
            return Boolean.TRUE.equals(timer.record(() -> tx.execute(s -> doEvaluate(attemptId, force))));
        }
        if (handle.isEmpty()) {
            return false;
        }
        try (RedisLock.Handle ignored = handle.get()) {
            return Boolean.TRUE.equals(timer.record(() -> tx.execute(s -> doEvaluate(attemptId, force))));
        }
    }

    private boolean doEvaluate(UUID attemptId, boolean force) {
        AttemptSnapshot attempt = attempts.lockForEvaluation(attemptId).orElse(null);
        if (attempt == null) {
            log.warn("Evaluation requested for unknown attempt {}", attemptId);
            return false;
        }
        boolean eligible = attempt.status() == AttemptStatus.SUBMITTED
                || (force && attempt.status() == AttemptStatus.EVALUATED);
        if (!eligible) {
            return false;
        }

        EvaluationSpecCache.Spec spec = specs.get(attempt.testId());
        Map<UUID, Response> responses = attempts.answers(attemptId).stream()
                .collect(Collectors.toMap(AnswerRow::questionId,
                        a -> new Response(a.questionId(), a.answer(), a.state(), a.timeSpentSeconds())));
        Evaluation ev = ScoringEngine.evaluate(spec.sections(), spec.questions(), responses);

        // Only rows that exist (visited questions) are updated; never-visited ones have no row.
        attempts.saveOutcomes(attemptId, ev.outcomes().stream()
                .filter(o -> responses.containsKey(o.questionId()))
                .map(o -> new OutcomeRow(o.questionId(), o.outcome().name(), o.marks()))
                .toList());

        Instant now = Instant.now(clock);
        int timeTaken = (int) Math.min(spec.test().durationMinutes() * 60L,
                Math.max(0, Duration.between(attempt.startedAt(),
                        attempt.submittedAt() == null ? now : attempt.submittedAt()).toSeconds()));

        Result result = results.findByAttemptId(attemptId).orElseGet(Result::new);
        result.setAttemptId(attemptId);
        result.setTestId(attempt.testId());
        result.setUserId(attempt.userId());
        result.setScore(ev.score());
        result.setMaxScore(spec.test().totalMarks());
        result.setCorrectCount(ev.correct());
        result.setIncorrectCount(ev.incorrect());
        result.setPartialCount(ev.partial());
        result.setUnattemptedCount(ev.unattempted());
        result.setAccuracy(ev.accuracy());
        result.setTimeTakenSeconds(timeTaken);
        result.setRanked(attempt.attemptNo() == 1);
        result.setRank(null);
        result.setPercentile(null);
        result.setRankFinal(false);
        result.setSectionScores(ev.sections());
        result.setTopicScores(ev.topics());
        result.setEvaluatedAt(now);
        results.save(result);
        attempts.markEvaluated(attemptId, now);

        if (result.isRanked()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    leaderboard.record(attempt.testId(), attempt.userId(), ev.score(), timeTaken);
                }
            });
        }
        events.publishEvent(new ResultEvaluatedEvent(result.getId(), attemptId, attempt.testId(), attempt.userId()));
        meters.counter("examprep.evaluation.completed", "forced", String.valueOf(force)).increment();
        log.info("Evaluated attempt {}: {} / {} ({} correct, {} incorrect, {} partial)", attemptId, ev.score(),
                spec.test().totalMarks(), ev.correct(), ev.incorrect(), ev.partial());
        return true;
    }
}
