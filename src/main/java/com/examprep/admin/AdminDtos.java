package com.examprep.admin;

import com.examprep.question.dto.QuestionSummaryDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record DailyPoint(LocalDate date, long count, BigDecimal amount) {
    }

    public record SeriesRevenue(UUID seriesId, String name, BigDecimal revenue, long payments) {
    }

    public record Revenue(BigDecimal total, BigDecimal last30Days, BigDecimal today, long paidPayments,
                          List<DailyPoint> daily, List<SeriesRevenue> topSeries) {
    }

    public record DashboardDto(Map<String, Long> usersByRole, long newStudentsLast7Days,
                               Map<String, Long> testsByStatus, long liveAttempts, long attemptsToday,
                               long attemptsTotal, List<DailyPoint> attemptsDaily, Revenue revenue) {
    }

    public record ScoreStats(long candidates, BigDecimal average, BigDecimal median, BigDecimal highest,
                             BigDecimal lowest, BigDecimal stdDev, BigDecimal averageAccuracy,
                             Integer averageTimeSeconds) {
    }

    public record Bucket(BigDecimal from, BigDecimal to, long count) {
    }

    /**
     * @param flag TOO_HARD (accuracy under 20%), TOO_EASY (over 90%), SKIPPED (attempt rate under 30%), or null
     */
    public record QuestionStat(int number, UUID questionId, String section, QuestionSummaryDto question,
                               long attempted, BigDecimal attemptRate, long correct, long incorrect, long partial,
                               BigDecimal accuracy, Integer avgTimeSeconds, String flag) {
    }

    public record TestStatsDto(UUID testId, String title, BigDecimal maxScore, Map<String, Long> attemptsByStatus,
                               ScoreStats scores, List<Bucket> distribution, List<QuestionStat> questions) {
    }
}
