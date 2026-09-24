package com.examprep.result.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Denormalised breakdowns stored as JSONB on {@code results} (section_scores, topic_scores).
 * Stored per result, so analytics can aggregate across a student's history with
 * {@code jsonb_array_elements} and never re-read attempt_answers.
 */
public final class ScoreBreakdowns {

    private ScoreBreakdowns() {
    }

    public record SectionScore(UUID sectionId, String name, UUID subjectId, int total, int attempted, int correct,
                               int incorrect, int partial, int unattempted, BigDecimal score, BigDecimal maxScore,
                               int timeSpentSeconds) {
    }

    public record TopicScore(UUID topicId, UUID chapterId, UUID subjectId, int total, int attempted, int correct,
                             int incorrect, int partial, BigDecimal score, BigDecimal maxScore,
                             int timeSpentSeconds) {
    }
}
