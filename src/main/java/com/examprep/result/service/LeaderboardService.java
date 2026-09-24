package com.examprep.result.service;

import com.examprep.result.repository.ResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Live leaderboard: one Redis sorted set per test ({@code leaderboard:test:{testId}}),
 * member = userId (ranked attempts only, so one per student).
 *
 * <p><b>Score encoding.</b> {@code composite = marksInHundredths × 10^6 + (999 999 − timeTakenSeconds)}.
 * Sorting by composite gives marks descending with less time first. It stays exact in a
 * double (max ~7.2e10, well under 2^53) and handles negative marks via floor division.
 *
 * <p><b>Rank uses marks only</b>, so equal marks means equal rank (competition ranking),
 * matching the final SQL {@code RANK()}. Time only orders the display within a tie.
 * Both are O(log n) {@code ZCOUNT}s:
 * <pre>
 *   rank       = 1 + ZCOUNT(key, (marks+0.01) band start, +inf)
 *   percentile = 100 × ZCOUNT(key, -inf, end of my marks band) / ZCARD(key)    (NTA formula)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    static final long BAND = 1_000_000L;
    private static final int MAX_TIME = 999_999;

    private final StringRedisTemplate redis;
    private final ResultRepository results;

    public record LiveRank(int rank, BigDecimal percentile, long totalCandidates) {
    }

    public record Entry(int rank, UUID userId, BigDecimal score, int timeTakenSeconds) {
    }

    // ------------------------------------------------------------------ encoding (package-visible for tests)

    static long composite(BigDecimal score, int timeTakenSeconds) {
        long hundredths = score.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
        return hundredths * BAND + (MAX_TIME - Math.min(Math.max(timeTakenSeconds, 0), MAX_TIME));
    }

    static long marksBand(long composite) {
        return Math.floorDiv(composite, BAND);
    }

    static BigDecimal scoreOf(long composite) {
        return BigDecimal.valueOf(marksBand(composite)).movePointLeft(2);
    }

    static int timeOf(long composite) {
        return (int) (MAX_TIME - Math.floorMod(composite, BAND));
    }

    // ------------------------------------------------------------------ operations

    public void record(UUID testId, UUID userId, BigDecimal score, int timeTakenSeconds) {
        try {
            redis.opsForZSet().add(key(testId), userId.toString(), composite(score, timeTakenSeconds));
        } catch (RuntimeException e) {
            log.warn("Leaderboard update failed for test {}: {}", testId, e.getMessage());
        }
    }

    public Optional<LiveRank> liveRank(UUID testId, UUID userId) {
        try {
            ensureLoaded(testId);
            Double mine = redis.opsForZSet().score(key(testId), userId.toString());
            if (mine == null) {
                return Optional.empty();
            }
            long band = marksBand(mine.longValue());
            Long higher = redis.opsForZSet().count(key(testId), (band + 1) * BAND, Double.POSITIVE_INFINITY);
            Long atOrBelow = redis.opsForZSet().count(key(testId), Double.NEGATIVE_INFINITY, band * BAND + (BAND - 1));
            Long total = redis.opsForZSet().zCard(key(testId));
            if (higher == null || atOrBelow == null || total == null || total == 0) {
                return Optional.empty();
            }
            BigDecimal percentile = BigDecimal.valueOf(atOrBelow * 100.0 / total).setScale(3, RoundingMode.HALF_UP);
            return Optional.of(new LiveRank((int) (higher + 1), percentile, total));
        } catch (RuntimeException e) {
            log.warn("Live rank unavailable for test {}: {}", testId, e.getMessage());
            return Optional.empty();
        }
    }

    /** Top N with competition ranks (ties share a rank). */
    public List<Entry> top(UUID testId, int limit) {
        ensureLoaded(testId);
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redis.opsForZSet().reverseRangeWithScores(key(testId), 0, limit - 1L);
        List<Entry> entries = new ArrayList<>();
        if (tuples == null) {
            return entries;
        }
        int position = 0;
        int rank = 0;
        long previousBand = Long.MIN_VALUE;
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            position++;
            long c = t.getScore().longValue();
            if (marksBand(c) != previousBand) {
                rank = position;
                previousBand = marksBand(c);
            }
            entries.add(new Entry(rank, UUID.fromString(t.getValue()), scoreOf(c), timeOf(c)));
        }
        return entries;
    }

    public long size(UUID testId) {
        Long n = redis.opsForZSet().zCard(key(testId));
        return n == null ? 0 : n;
    }

    /** Recreates the set from Postgres if Redis lost it (e.g. flushed). Postgres results are the source of truth. */
    public void ensureLoaded(UUID testId) {
        Long n = redis.opsForZSet().zCard(key(testId));
        if (n != null && n > 0) {
            return;
        }
        rebuild(testId);
    }

    public void rebuild(UUID testId) {
        List<Object[]> rows = results.rankedScores(testId);
        redis.delete(key(testId));
        if (rows.isEmpty()) {
            return;
        }
        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
        for (Object[] r : rows) {
            tuples.add(ZSetOperations.TypedTuple.of(r[0].toString(),
                    (double) composite((BigDecimal) r[1], ((Number) r[2]).intValue())));
        }
        redis.opsForZSet().add(key(testId), tuples);
        log.info("Leaderboard for test {} rebuilt from {} results", testId, rows.size());
    }

    private static String key(UUID testId) {
        return "leaderboard:test:" + testId;
    }
}
