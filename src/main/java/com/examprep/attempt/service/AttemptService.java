package com.examprep.attempt.service;

import com.examprep.attempt.AttemptProperties;
import com.examprep.attempt.dto.AttemptDtos.AnswerStateDto;
import com.examprep.attempt.dto.AttemptDtos.AntiCheatDto;
import com.examprep.attempt.dto.AttemptDtos.AttemptSessionDto;
import com.examprep.attempt.dto.AttemptDtos.AttemptSummaryDto;
import com.examprep.attempt.dto.AttemptDtos.TimeDto;
import com.examprep.attempt.entity.Attempt;
import com.examprep.attempt.entity.AttemptEventType;
import com.examprep.attempt.entity.AttemptStatus;
import com.examprep.attempt.entity.SubmitType;
import com.examprep.attempt.paper.PaperService;
import com.examprep.attempt.paper.PaperShuffler;
import com.examprep.attempt.repository.AttemptRepository;
import com.examprep.attempt.store.AttemptRedisStore;
import com.examprep.attempt.store.AttemptRedisStore.AttemptMeta;
import com.examprep.attempt.store.AttemptRedisStore.SavedAnswer;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.security.AuthUser;
import com.examprep.test.dto.TestLookupDtos.TestSnapshot;
import com.examprep.test.service.TestLookupService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Start / resume / read of attempts.
 *
 * <p>"Start" is <b>idempotent</b>: while an attempt is running, calling it again (page
 * refresh, a second tab, a reconnect) returns the same attempt with the saved answers and
 * the unchanged server deadline. That is the whole resume story for the client.
 */
@Slf4j
@Service
public class AttemptService {

    private static final SecureRandom SEEDS = new SecureRandom();

    private final AttemptRepository repository;
    private final AttemptRedisStore store;
    private final AttemptAccess access;
    private final SubmissionService submissions;
    private final PaperService papers;
    private final TestLookupService tests;
    private final AttemptProperties props;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final MeterRegistry meters;

    public AttemptService(AttemptRepository repository, AttemptRedisStore store, AttemptAccess access,
                          SubmissionService submissions, PaperService papers, TestLookupService tests,
                          AttemptProperties props, ObjectMapper objectMapper,
                          PlatformTransactionManager transactionManager, Clock clock, MeterRegistry meters) {
        this.repository = repository;
        this.store = store;
        this.access = access;
        this.submissions = submissions;
        this.papers = papers;
        this.tests = tests;
        this.props = props;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.meters = meters;
    }

    public AttemptSessionDto start(AuthUser user, UUID testId, String clientIp, String userAgent) {
        Instant now = Instant.now(clock);

        // 1) Resume a running attempt (no access re-check: it was granted at start).
        Optional<Attempt> running = repository.findFirstByTestIdAndUserIdAndStatus(testId, user.id(),
                AttemptStatus.IN_PROGRESS);
        if (running.isPresent()) {
            Attempt a = running.get();
            if (now.isBefore(a.getDeadlineAt().plus(props.autosaveGrace()))) {
                appendServerEvent(a.getId(), AttemptEventType.RESUMED, now);
                meters.counter("examprep.attempt.resumed").increment();
                return session(access.requireOwn(user.id(), a.getId()), now);
            }
            submissions.submit(a.getId(), null, SubmitType.AUTO, "Time expired before resume");
        }

        // 2) New attempt: access, window and attempt limits.
        TestSnapshot test = tests.requireStartable(user, testId, now);
        long used = repository.countByTestIdAndUserIdAndStatusNot(testId, user.id(), AttemptStatus.CANCELLED);
        if (used >= test.maxAttempts()) {
            throw new BusinessException(ErrorCode.NO_ATTEMPTS_LEFT);
        }
        Instant deadline = now.plus(Duration.ofMinutes(test.durationMinutes()));
        if (test.endAt() != null && test.endAt().isBefore(deadline)) {
            deadline = test.endAt();   // late joiners get only the remaining window, like a real exam hall
        }

        Attempt attempt = new Attempt();
        attempt.setTestId(testId);
        attempt.setUserId(user.id());
        attempt.setAttemptNo((int) used + 1);
        attempt.setStartedAt(now);
        attempt.setDeadlineAt(deadline);
        attempt.setShuffleSeed(SEEDS.nextLong());
        attempt.setClientIp(clientIp);
        attempt.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(500, userAgent.length())));
        try {
            tx.executeWithoutResult(s -> repository.saveAndFlush(attempt));
        } catch (DataIntegrityViolationException e) {
            // A concurrent "start" (double click, two tabs) won the race, guarded by
            // ux_attempts_one_in_progress. Resume theirs instead.
            Attempt winner = repository.findFirstByTestIdAndUserIdAndStatus(testId, user.id(), AttemptStatus.IN_PROGRESS)
                    .orElseThrow(() -> e);
            return session(access.requireOwn(user.id(), winner.getId()), now);
        }

        store.init(attempt, test.shuffleQuestions(), test.shuffleOptions());
        appendServerEvent(attempt.getId(), AttemptEventType.STARTED, now);
        meters.counter("examprep.attempt.started").increment();
        log.info("Attempt {} started by {} on test {} (deadline {})", attempt.getId(), user.id(), testId, deadline);
        return session(access.requireOwn(user.id(), attempt.getId()), now);
    }

    public AttemptSessionDto session(AuthUser user, UUID attemptId) {
        return session(access.requireOwn(user.id(), attemptId), Instant.now(clock));
    }

    public TimeDto time(AuthUser user, UUID attemptId) {
        AttemptMeta meta = access.requireOwn(user.id(), attemptId);
        Instant now = Instant.now(clock);
        return new TimeDto(meta.status(), now, meta.deadline(), remaining(meta, now));
    }

    @Transactional(readOnly = true)
    public List<AttemptSummaryDto> myAttempts(AuthUser user, UUID testId) {
        return repository.findByTestIdAndUserIdOrderByAttemptNoDesc(testId, user.id()).stream()
                .map(a -> a.getStatus() == AttemptStatus.IN_PROGRESS
                        ? SubmissionService.summary(a, 0, 0, 0)
                        : submissions.summaryFromDb(a))
                .toList();
    }

    // ------------------------------------------------------------------ helpers

    private AttemptSessionDto session(AttemptMeta meta, Instant now) {
        AntiCheatDto antiCheat = new AntiCheatDto(meta.tabSwitches(), meta.fullscreenExits(), props.maxTabSwitches(),
                false);
        if (meta.status() != AttemptStatus.IN_PROGRESS) {
            // Finished: the client navigates to the result page. The paper is not re-sent.
            return new AttemptSessionDto(meta.attemptId(), meta.testId(), 0, meta.status(), meta.startedAt(),
                    meta.deadline(), now, 0, null, Map.of(), 0, antiCheat);
        }
        Map<UUID, SavedAnswer> saved = store.answers(meta.attemptId());
        Map<UUID, AnswerStateDto> answers = new HashMap<>();
        long lastSeq = 0;
        for (Map.Entry<UUID, SavedAnswer> e : saved.entrySet()) {
            SavedAnswer s = e.getValue();
            answers.put(e.getKey(), new AnswerStateDto(s.answer(), s.state(), s.timeSpentSeconds(), s.visits(), s.seq()));
            lastSeq = Math.max(lastSeq, s.seq());
        }
        int attemptNo = repository.findById(meta.attemptId()).map(Attempt::getAttemptNo).orElse(1);
        return new AttemptSessionDto(meta.attemptId(), meta.testId(), attemptNo, meta.status(), meta.startedAt(),
                meta.deadline(), now, remaining(meta, now),
                PaperShuffler.forAttempt(papers.paper(meta.testId()), meta.seed(), meta.shuffleQuestions(),
                        meta.shuffleOptions()),
                answers, lastSeq, antiCheat);
    }

    static long remaining(AttemptMeta meta, Instant now) {
        if (meta.status() != AttemptStatus.IN_PROGRESS) {
            return 0;
        }
        return Math.max(0, Duration.between(now, meta.deadline()).toSeconds());
    }

    private void appendServerEvent(UUID attemptId, AttemptEventType type, Instant now) {
        try {
            store.appendEvents(attemptId, List.of(objectMapper.writeValueAsString(
                    Map.of("type", type.name(), "serverTs", now.toString()))));
        } catch (JsonProcessingException | RuntimeException e) {
            log.debug("Could not buffer {} event for {}: {}", type, attemptId, e.getMessage());
        }
    }
}
