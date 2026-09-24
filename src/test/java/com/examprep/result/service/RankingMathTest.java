package com.examprep.result.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Leaderboard encoding and the rank/percentile rules (the Redis path must agree with the final SQL). */
class RankingMathTest {

    @Test
    void composite_orders_by_marks_then_less_time_and_round_trips() {
        long fast = LeaderboardService.composite(new BigDecimal("180.25"), 3000);
        long slow = LeaderboardService.composite(new BigDecimal("180.25"), 6000);
        long lower = LeaderboardService.composite(new BigDecimal("180.00"), 10);
        assertThat(fast).isGreaterThan(slow).isGreaterThan(lower);

        assertThat(LeaderboardService.scoreOf(fast)).isEqualByComparingTo("180.25");
        assertThat(LeaderboardService.timeOf(fast)).isEqualTo(3000);
        assertThat(LeaderboardService.marksBand(fast)).isEqualTo(LeaderboardService.marksBand(slow));
    }

    @Test
    void negative_scores_encode_and_sort_correctly() {
        long minusFive = LeaderboardService.composite(new BigDecimal("-5"), 100);
        long zero = LeaderboardService.composite(BigDecimal.ZERO, 5000);
        assertThat(minusFive).isLessThan(zero);
        assertThat(LeaderboardService.scoreOf(minusFive)).isEqualByComparingTo("-5");
        assertThat(LeaderboardService.timeOf(minusFive)).isEqualTo(100);
    }

    @Test
    void max_realistic_value_is_exact_in_a_double() {
        long c = LeaderboardService.composite(new BigDecimal("720.00"), 0);
        assertThat((long) (double) c).isEqualTo(c);                  // Redis zset scores are doubles
        assertThat(c).isLessThan(1L << 53);
    }

    /**
     * Reference implementation of the rules used by both Redis and SQL:
     * rank = 1 + (number with strictly higher marks), percentile = 100 × (number with marks ≤ mine) / N.
     */
    @Test
    void competition_ranking_and_nta_percentile_with_ties() {
        List<Integer> scores = List.of(40, 2, 2, 0);
        int[] ranks = scores.stream().mapToInt(s -> 1 + (int) scores.stream().filter(o -> o > s).count()).toArray();
        double[] pct = scores.stream().mapToDouble(s -> 100.0 * scores.stream().filter(o -> o <= s).count() / scores.size())
                .toArray();
        assertThat(ranks).containsExactly(1, 2, 2, 4);
        assertThat(pct).containsExactly(100.0, 75.0, 75.0, 25.0);

        // Everyone tied: all rank 1, all at the 100th percentile.
        List<Integer> tied = IntStream.range(0, 5).map(i -> 10).boxed().toList();
        assertThat(tied.stream().mapToInt(s -> 1 + (int) tied.stream().filter(o -> o > s).count()).distinct())
                .containsExactly(1);
    }

    @Test
    void leaderboard_names_are_shortened_for_privacy() {
        assertThat(ResultQueryService.shortName("Riya Sharma")).isEqualTo("Riya S.");
        assertThat(ResultQueryService.shortName("Arjun Kumar Mehta")).isEqualTo("Arjun M.");
        assertThat(ResultQueryService.shortName("Madonna")).isEqualTo("Madonna");
        assertThat(ResultQueryService.shortName(null)).isEqualTo("Student");
    }
}
