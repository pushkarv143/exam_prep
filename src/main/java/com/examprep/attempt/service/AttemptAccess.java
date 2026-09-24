package com.examprep.attempt.service;

import com.examprep.attempt.AttemptProperties;
import com.examprep.attempt.entity.Attempt;
import com.examprep.attempt.entity.AttemptStatus;
import com.examprep.attempt.repository.AttemptRepository;
import com.examprep.attempt.store.AttemptRedisStore;
import com.examprep.attempt.store.AttemptRedisStore.AttemptMeta;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.exception.NotFoundException;
import com.examprep.test.dto.TestLookupDtos.TestSnapshot;
import com.examprep.test.service.TestLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Resolves an attempt's live state for its owner. The normal path is Redis only (no DB
 * query per autosave). If the Redis state is missing (for example Redis was replaced),
 * it is rebuilt from the attempt row. Answers autosaved before the loss are gone in that
 * case, which is why Redis runs with AOF persistence.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AttemptAccess {

    private final AttemptRedisStore store;
    private final AttemptRepository repository;
    private final TestLookupService tests;
    private final AttemptProperties props;

    AttemptMeta requireOwn(UUID userId, UUID attemptId) {
        AttemptMeta meta = store.meta(attemptId).orElseGet(() -> rebuild(attemptId));
        if (!meta.userId().equals(userId)) {
            throw NotFoundException.of("Attempt", attemptId);   // never reveal other users' attempts
        }
        return meta;
    }

    AttemptMeta requireOwnInProgress(UUID userId, UUID attemptId, Instant now) {
        AttemptMeta meta = requireOwn(userId, attemptId);
        if (meta.status() != AttemptStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.ATTEMPT_NOT_IN_PROGRESS);
        }
        if (now.isAfter(meta.deadline().plus(props.autosaveGrace()))) {
            throw new BusinessException(ErrorCode.ATTEMPT_EXPIRED);
        }
        return meta;
    }

    /** Recreates the Redis meta from Postgres (in-progress attempts only). Returns the meta either way. */
    AttemptMeta rebuild(UUID attemptId) {
        Attempt attempt = repository.findById(attemptId).orElseThrow(() -> NotFoundException.of("Attempt", attemptId));
        TestSnapshot test = tests.snapshot(attempt.getTestId());
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            log.warn("Rebuilding missing Redis state for attempt {}", attemptId);
            store.init(attempt, test.shuffleQuestions(), test.shuffleOptions());
        }
        return new AttemptMeta(attempt.getId(), attempt.getUserId(), attempt.getTestId(), attempt.getStartedAt(),
                attempt.getDeadlineAt(), attempt.getStatus(), attempt.getShuffleSeed(), test.shuffleQuestions(),
                test.shuffleOptions(), attempt.getTabSwitchCount(), attempt.getFullscreenExitCount());
    }
}
