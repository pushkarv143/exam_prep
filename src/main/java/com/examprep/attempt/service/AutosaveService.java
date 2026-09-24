package com.examprep.attempt.service;

import com.examprep.attempt.AttemptProperties;
import com.examprep.attempt.dto.AttemptDtos.AnswerChange;
import com.examprep.attempt.dto.AttemptDtos.AutosaveRequest;
import com.examprep.attempt.dto.AttemptDtos.AutosaveResult;
import com.examprep.attempt.dto.AttemptDtos.RejectedChange;
import com.examprep.attempt.entity.AnswerState;
import com.examprep.attempt.model.StudentAnswer;
import com.examprep.attempt.paper.PaperIndex;
import com.examprep.attempt.paper.PaperService;
import com.examprep.attempt.store.AttemptRedisStore;
import com.examprep.attempt.store.AttemptRedisStore.AnswerWrite;
import com.examprep.attempt.store.AttemptRedisStore.AttemptMeta;
import com.examprep.attempt.store.AttemptRedisStore.SavedAnswer;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.security.AuthUser;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Autosave, the hottest endpoint (roughly 50k students / 15 s = 3.3k req/s). Per request:
 * one Redis HGETALL on a tiny meta hash, in-memory validation against the cached paper
 * index, and one Lua EVAL. Postgres is not touched.
 * (Sections with an "attempt any N" limit add one HGETALL of the answers hash.)
 */
@Service
public class AutosaveService {

    private static final int MAX_VISITS = 10_000;

    private final AttemptAccess access;
    private final AttemptRedisStore store;
    private final PaperService papers;
    private final AnswerValidator validator;
    private final AttemptProperties props;
    private final Clock clock;
    private final Counter requests;
    private final Counter rejectedCounter;

    public AutosaveService(AttemptAccess access, AttemptRedisStore store, PaperService papers,
                           AnswerValidator validator, AttemptProperties props, Clock clock, MeterRegistry meters) {
        this.access = access;
        this.store = store;
        this.papers = papers;
        this.validator = validator;
        this.props = props;
        this.clock = clock;
        this.requests = meters.counter("examprep.autosave.requests");
        this.rejectedCounter = meters.counter("examprep.autosave.rejected_changes");
    }

    public AutosaveResult save(AuthUser user, UUID attemptId, AutosaveRequest request) {
        requests.increment();
        Instant now = Instant.now(clock);
        AttemptMeta meta = access.requireOwnInProgress(user.id(), attemptId, now);
        if (request.changes().size() > props.maxChangesPerAutosave()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Too many changes in one autosave");
        }
        PaperIndex index = papers.index(meta.testId());

        List<RejectedChange> rejected = new ArrayList<>();
        List<AnswerWrite> writes = new ArrayList<>(request.changes().size());
        // Elapsed-time cap: time-on-question can never exceed time since start (plus clock slack).
        int maxSeconds = (int) Math.max(0, Duration.between(meta.startedAt(), now).toSeconds() + 5);
        SectionLimiter limiter = index.sectionLimits().isEmpty() ? null : new SectionLimiter(index, store.answers(attemptId));

        for (AnswerChange change : request.changes()) {
            PaperIndex.Entry q = index.question(change.questionId());
            if (q == null) {
                rejected.add(new RejectedChange(change.questionId(), "Question is not part of this test"));
                continue;
            }
            StudentAnswer answer;
            try {
                answer = validator.normalize(q, change.answer());
            } catch (AnswerValidator.InvalidAnswerException e) {
                rejected.add(new RejectedChange(change.questionId(), e.getMessage()));
                continue;
            }
            if (limiter != null && !limiter.allow(change.questionId(), q.sectionId(), answer != null)) {
                rejected.add(new RejectedChange(change.questionId(), "This section allows only "
                        + index.sectionLimits().get(q.sectionId()) + " answered questions; clear another answer first"));
                continue;
            }
            writes.add(new AnswerWrite(change.questionId(), change.seq(), answer,
                    AnswerState.of(answer != null, change.markedForReview()),
                    Math.min(change.timeSpentSeconds(), maxSeconds), Math.min(change.visits(), MAX_VISITS)));
        }
        rejectedCounter.increment(rejected.size());

        long applied = writes.isEmpty() ? 0 : store.applyAnswers(attemptId, now, writes);
        if (applied == AttemptRedisStore.META_MISSING) {
            access.rebuild(attemptId);
            applied = store.applyAnswers(attemptId, now, writes);
        }
        if (applied == AttemptRedisStore.NOT_IN_PROGRESS) {
            throw new BusinessException(ErrorCode.ATTEMPT_NOT_IN_PROGRESS);
        }
        if (applied == AttemptRedisStore.EXPIRED) {
            throw new BusinessException(ErrorCode.ATTEMPT_EXPIRED);
        }
        return new AutosaveResult((int) Math.max(0, applied), rejected, now, AttemptService.remaining(meta, now));
    }

    /**
     * Enforces "attempt any N" per section, simulating the batch in order over the stored
     * states. Clearing an answer frees a slot, so clear-then-answer in one batch works.
     */
    static final class SectionLimiter {

        private final PaperIndex index;
        private final Map<UUID, Boolean> answered = new HashMap<>();
        private final Map<UUID, Integer> countBySection = new HashMap<>();

        SectionLimiter(PaperIndex index, Map<UUID, SavedAnswer> current) {
            this.index = index;
            current.forEach((qid, saved) -> {
                PaperIndex.Entry q = index.question(qid);
                if (q != null && saved.state().hasAnswer()) {
                    answered.put(qid, true);
                    countBySection.merge(q.sectionId(), 1, Integer::sum);
                }
            });
        }

        boolean allow(UUID questionId, UUID sectionId, boolean hasAnswer) {
            Integer limit = index.sectionLimits().get(sectionId);
            boolean wasAnswered = answered.getOrDefault(questionId, false);
            if (limit != null && hasAnswer && !wasAnswered && countBySection.getOrDefault(sectionId, 0) >= limit) {
                return false;
            }
            if (hasAnswer != wasAnswered) {
                answered.put(questionId, hasAnswer);
                countBySection.merge(sectionId, hasAnswer ? 1 : -1, Integer::sum);
            }
            return true;
        }
    }
}
