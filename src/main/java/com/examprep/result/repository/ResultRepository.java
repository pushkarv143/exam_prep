package com.examprep.result.repository;

import com.examprep.result.entity.Result;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResultRepository extends JpaRepository<Result, UUID> {

    Optional<Result> findByAttemptId(UUID attemptId);

    Page<Result> findByTestIdAndRankedTrue(UUID testId, Pageable pageable);

    /** Uses ix_results_test_user. */
    Optional<Result> findFirstByTestIdAndUserIdAndRankedTrue(UUID testId, UUID userId);

    Page<Result> findByTestId(UUID testId, Pageable pageable);

    long countByTestIdAndRankedTrue(UUID testId);

    /**
     * Final ranks and percentiles in one set-based statement (uses ix_results_test_score).
     * <ul>
     *   <li>{@code RANK()} is standard competition ranking: equal scores share a rank and the
     *       next rank skips (1, 2, 2, 4).</li>
     *   <li>{@code CUME_DIST() OVER (ORDER BY score)} is exactly the NTA percentile:
     *       100 × (candidates with score ≤ mine) / (total candidates). The topper gets 100.</li>
     * </ul>
     */
    @Modifying
    @Query(value = """
            UPDATE results r
               SET rank = x.rnk, percentile = x.pct, rank_final = TRUE, version = r.version + 1, updated_at = :now
              FROM (SELECT id,
                           RANK() OVER (ORDER BY score DESC) AS rnk,
                           ROUND((CUME_DIST() OVER (ORDER BY score) * 100)::numeric, 3) AS pct
                      FROM results WHERE test_id = :testId AND is_ranked) x
             WHERE r.id = x.id
            """, nativeQuery = true)
    int finalizeRanks(@Param("testId") UUID testId, @Param("now") Instant now);

    /** [userId, score, timeTakenSeconds] of ranked results, for rebuilding the Redis leaderboard. */
    @Query("select r.userId, r.score, r.timeTakenSeconds from Result r where r.testId = :testId and r.ranked = true")
    List<Object[]> rankedScores(@Param("testId") UUID testId);

    /** Scheduled tests whose window has closed but whose ranks are not final yet (uses ix_tests_rank_pending). */
    @Query(value = """
            SELECT id FROM tests
             WHERE ranks_computed_at IS NULL AND end_at IS NOT NULL AND end_at < :cutoff
               AND status IN ('PUBLISHED', 'LIVE', 'COMPLETED')
             ORDER BY end_at LIMIT 20
            """, nativeQuery = true)
    List<UUID> testsAwaitingFinalRanks(@Param("cutoff") Instant cutoff);

    /** Attempts of a test that could still change the ranking (running, or submitted but not evaluated). */
    @Query(value = "SELECT count(*) FROM attempts WHERE test_id = :testId AND status IN ('IN_PROGRESS', 'SUBMITTED')",
            nativeQuery = true)
    long countUnfinishedAttempts(@Param("testId") UUID testId);
}
