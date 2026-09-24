package com.examprep.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record Summary(int testsTaken, BigDecimal averagePercentage, BigDecimal averageAccuracy,
                          BigDecimal bestPercentile, int questionsAttempted, long totalTimeSeconds) {
    }

    /** One point of the score-trend chart. {@code practice} = re-attempt (not ranked). */
    public record TrendPoint(UUID attemptId, UUID testId, String testTitle, BigDecimal score, BigDecimal maxScore,
                             BigDecimal percentage, Integer rank, BigDecimal percentile, boolean practice,
                             Instant evaluatedAt) {
    }

    public record SubjectStrength(UUID subjectId, String subjectName, int attempted, int correct, int incorrect,
                                  BigDecimal accuracy, BigDecimal scorePercentage, long timeSpentSeconds) {
    }

    public record TopicStrength(UUID topicId, String topicName, String chapterName, String subjectName, int total,
                                int attempted, int correct, int incorrect, BigDecimal accuracy,
                                BigDecimal scorePercentage, long avgSecondsPerQuestion) {
    }

    /**
     * @param weakTopics   accuracy below 50%, with at least {@code minAttempts} attempted (worst first)
     * @param strongTopics accuracy of 75% or more, with at least {@code minAttempts} attempted (best first)
     */
    public record OverviewDto(Summary summary, List<TrendPoint> trend, List<SubjectStrength> subjects,
                              List<TopicStrength> weakTopics, List<TopicStrength> strongTopics,
                              List<TopicStrength> allTopics, int minAttempts) {
    }
}
