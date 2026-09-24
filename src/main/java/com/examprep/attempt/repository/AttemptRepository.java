package com.examprep.attempt.repository;

import com.examprep.attempt.entity.Attempt;
import com.examprep.attempt.entity.AttemptStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttemptRepository extends JpaRepository<Attempt, UUID> {

    /** SELECT ... FOR UPDATE: serialises manual submit, auto-submit and the anti-cheat auto-submit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Attempt a where a.id = :id")
    Optional<Attempt> lockById(@Param("id") UUID id);

    Optional<Attempt> findFirstByTestIdAndUserIdAndStatus(UUID testId, UUID userId, AttemptStatus status);

    long countByTestIdAndUserIdAndStatusNot(UUID testId, UUID userId, AttemptStatus status);

    List<Attempt> findByTestIdAndUserIdOrderByAttemptNoDesc(UUID testId, UUID userId);

    /** Fallback sweep for attempts whose Redis deadline entry was lost. Uses ix_attempts_in_progress_deadline. */
    @Query("""
            select a.id from Attempt a
            where a.status = com.examprep.attempt.entity.AttemptStatus.IN_PROGRESS and a.deadlineAt < :cutoff
            order by a.deadlineAt
            """)
    List<UUID> findExpiredInProgressIds(@Param("cutoff") Instant cutoff, Limit limit);
}
