package com.examprep.attempt.service;

import com.examprep.attempt.dto.AttemptDtos.AttemptSummaryDto;
import com.examprep.attempt.entity.AnswerState;
import com.examprep.attempt.entity.Attempt;
import com.examprep.attempt.entity.AttemptEventType;
import com.examprep.attempt.entity.AttemptStatus;
import com.examprep.attempt.entity.SubmitType;
import com.examprep.attempt.event.AttemptSubmittedEvent;
import com.examprep.attempt.paper.PaperIndex;
import com.examprep.attempt.paper.PaperService;
import com.examprep.attempt.repository.AttemptAnswerRepository;
import com.examprep.attempt.repository.AttemptRepository;
import com.examprep.attempt.store.AttemptRedisStore;
import com.examprep.attempt.store.AttemptRedisStore.SavedAnswer;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.exception.NotFoundException;
import com.examprep.common.redis.RedisLock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Submit: the only time a running attempt touches Postgres.
 *
 * <ol>
 *   <li>Redis lock {@code lock:attempt:{id}}. Concurrent submits (double click,
 *       auto-submit racing a manual submit) do not pile up on the DB.</li>
 *   <li>DB row lock plus status check. This is the real guarantee: the first transaction
 *       flips the attempt to SUBMITTED, and every later call sees that and returns the
 *       same summary (idempotent).</li>
 *   <li>One JDBC batch upsert of all answers (about 90-180 rows, a single hash
 *       partition) and one batch of events.</li>
 *   <li>After commit: mark Redis state submitted and leave the deadline queue. Listeners
 *       (evaluation, Phase 5) receive {@link AttemptSubmittedEvent}.</li>
 * </ol>
 */
@Slf4j
@Service
public class SubmissionService {

    private static final String UPSERT_ANSWER = """
            INSERT INTO attempt_answers (attempt_id, question_id, section_id, answer, state, time_spent_seconds,
                                         visit_count, last_updated_at)
            VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?)
            ON CONFLICT (attempt_id, question_id) DO UPDATE SET
                answer = EXCLUDED.answer, state = EXCLUDED.state, time_spent_seconds = EXCLUDED.time_spent_seconds,
                visit_count = EXCLUDED.visit_count, last_updated_at = EXCLUDED.last_updated_at
            """;
    private static final String INSERT_EVENT = """
            INSERT INTO attempt_events (attempt_id, user_id, event_type, payload, client_ts, server_ts)
            VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?)
            """;

    private final AttemptRepository repository;
    private final AttemptAnswerRepository answerRepository;
    private final AttemptRedisStore store;
    private final RedisLock lock;
    private final PaperService papers;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MeterRegistry meters;
    private final Timer submitTimer;

    public SubmissionService(AttemptRepository repository, AttemptAnswerRepository answerRepository,
                             AttemptRedisStore store, RedisLock lock, PaperService papers, JdbcTemplate jdbc,
                             PlatformTransactionManager transactionManager, ApplicationEventPublisher events,
                             ObjectMapper objectMapper, Clock clock, MeterRegistry meters) {
        this.repository = repository;
        this.answerRepository = answerRepository;
        this.store = store;
        this.lock = lock;
        this.papers = papers;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.meters = meters;
        this.submitTimer = meters.timer("examprep.attempt.submit.duration");
    }

    /**
     * @param requesterId the student submitting, or null for system auto-submit
     * @return the summary, or empty if another node is already submitting this attempt (system calls only)
     * @throws BusinessException SUBMIT_IN_PROGRESS if a student's submit races another submit
     */
    public Optional<AttemptSummaryDto> submit(UUID attemptId, UUID requesterId, SubmitType type, String reason) {
        Optional<RedisLock.Handle> handle;
        try {
            handle = lock.tryAcquire("lock:attempt:" + attemptId, Duration.ofSeconds(30));
        } catch (RuntimeException e) {
            log.warn("Submit lock unavailable ({}); relying on the DB row lock", e.getMessage());
            return Optional.ofNullable(submitTimer.record(() -> tx.execute(s -> doSubmit(attemptId, requesterId, type, reason))));
        }
        if (handle.isEmpty()) {
            if (requesterId != null) {
                throw new BusinessException(ErrorCode.SUBMIT_IN_PROGRESS);
            }
            return Optional.empty();
        }
        try (RedisLock.Handle ignored = handle.get()) {
            return Optional.ofNullable(submitTimer.record(() -> tx.execute(s -> doSubmit(attemptId, requesterId, type, reason))));
        }
    }

    private AttemptSummaryDto doSubmit(UUID attemptId, UUID requesterId, SubmitType type, String reason) {
        Attempt attempt = repository.lockById(attemptId)
                .filter(a -> requesterId == null || a.getUserId().equals(requesterId))
                .orElseThrow(() -> NotFoundException.of("Attempt", attemptId));
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            return summaryFromDb(attempt);   // idempotent repeat
        }

        Instant now = Instant.now(clock);
        Map<UUID, SavedAnswer> saved = readOrEmpty(() -> store.answers(attemptId), attemptId, "answers");
        List<String> bufferedEvents = readOrEmpty(() -> store.events(attemptId), attemptId, "events");
        store.meta(attemptId).ifPresent(m -> {
            attempt.setTabSwitchCount(Math.max(attempt.getTabSwitchCount(), m.tabSwitches()));
            attempt.setFullscreenExitCount(Math.max(attempt.getFullscreenExitCount(), m.fullscreenExits()));
        });

        PaperIndex index = papers.index(attempt.getTestId());
        List<Object[]> rows = new ArrayList<>(saved.size());
        for (Map.Entry<UUID, SavedAnswer> e : saved.entrySet()) {
            PaperIndex.Entry q = index.question(e.getKey());
            if (q == null) {
                continue;   // question no longer in the paper (cannot happen for published tests)
            }
            SavedAnswer a = e.getValue();
            rows.add(new Object[]{attemptId, e.getKey(), q.sectionId(), a.answer() == null ? null : json(a.answer()),
                    a.state().name(), a.timeSpentSeconds(), Math.max(1, a.visits()), Timestamp.from(now)});
        }
        jdbc.batchUpdate(UPSERT_ANSWER, rows);

        List<Object[]> eventRows = new ArrayList<>(bufferedEvents.size() + 1);
        for (String raw : bufferedEvents) {
            eventRows.add(eventRow(attempt, raw));
        }
        AttemptEventType serverEvent = type == SubmitType.AUTO ? AttemptEventType.AUTO_SUBMITTED : AttemptEventType.SUBMITTED;
        eventRows.add(new Object[]{attemptId, attempt.getUserId(), serverEvent.name(),
                json(reason == null ? Map.of() : Map.of("reason", reason)), null, Timestamp.from(now)});
        jdbc.batchUpdate(INSERT_EVENT, eventRows);

        attempt.setStatus(AttemptStatus.SUBMITTED);
        attempt.setSubmittedAt(now);
        attempt.setSubmitType(type);

        events.publishEvent(new AttemptSubmittedEvent(attemptId, attempt.getTestId(), attempt.getUserId(), type));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    store.markSubmitted(attemptId, AttemptStatus.SUBMITTED);
                } catch (RuntimeException e) {
                    log.warn("Could not mark attempt {} submitted in Redis: {}", attemptId, e.getMessage());
                }
            }
        });
        meters.counter("examprep.attempt.submitted", "type", type.name()).increment();
        log.info("Attempt {} submitted ({}, {} answers{})", attemptId, type, rows.size(),
                reason == null ? "" : ", reason: " + reason);

        int answered = (int) saved.values().stream().filter(a -> a.state().hasAnswer()).count();
        int marked = (int) saved.values().stream().filter(a -> a.state().isMarked()).count();
        return summary(attempt, answered, marked, saved.size());
    }

    AttemptSummaryDto summaryFromDb(Attempt attempt) {
        int answered = 0;
        int marked = 0;
        int visited = 0;
        for (Object[] row : answerRepository.countByState(attempt.getId())) {
            AnswerState state = (AnswerState) row[0];
            int n = ((Number) row[1]).intValue();
            visited += n;
            if (state.hasAnswer()) {
                answered += n;
            }
            if (state.isMarked()) {
                marked += n;
            }
        }
        return summary(attempt, answered, marked, visited);
    }

    static AttemptSummaryDto summary(Attempt a, int answered, int marked, int visited) {
        return new AttemptSummaryDto(a.getId(), a.getTestId(), a.getAttemptNo(), a.getStatus(), a.getSubmitType(),
                a.getStartedAt(), a.getDeadlineAt(), a.getSubmittedAt(), answered, marked, visited,
                a.getTabSwitchCount(), a.getFullscreenExitCount());
    }

    private Object[] eventRow(Attempt attempt, String raw) {
        try {
            JsonNode n = objectMapper.readTree(raw);
            Timestamp clientTs = n.hasNonNull("clientTs") ? Timestamp.from(Instant.parse(n.get("clientTs").asText())) : null;
            Timestamp serverTs = Timestamp.from(Instant.parse(n.get("serverTs").asText()));
            return new Object[]{attempt.getId(), attempt.getUserId(), n.get("type").asText(),
                    n.has("details") ? n.get("details").toString() : "{}", clientTs, serverTs};
        } catch (JsonProcessingException | RuntimeException e) {
            return new Object[]{attempt.getId(), attempt.getUserId(), "UNPARSEABLE", json(Map.of("raw", raw)), null,
                    Timestamp.from(Instant.now(clock))};
        }
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A Redis failure at submit must not lose the submit itself. What was saved in Postgres is kept. */
    private <T> T readOrEmpty(java.util.function.Supplier<T> read, UUID attemptId, String what) {
        try {
            return read.get();
        } catch (RuntimeException e) {
            log.error("Could not read {} of attempt {} from Redis; submitting without them: {}", what, attemptId,
                    e.getMessage());
            @SuppressWarnings("unchecked")
            T empty = (T) (what.equals("answers") ? Map.of() : List.of());
            return empty;
        }
    }
}
