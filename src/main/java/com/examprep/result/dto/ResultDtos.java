package com.examprep.result.dto;

import com.examprep.attempt.entity.AnswerState;
import com.examprep.attempt.model.StudentAnswer;
import com.examprep.question.dto.StudentQuestionView.PassageView;
import com.examprep.question.entity.Language;
import com.examprep.question.entity.QuestionType;
import com.examprep.question.model.AnswerKey;
import com.examprep.question.model.QuestionContent.MatchItem;
import com.examprep.question.model.QuestionContent.Media;
import com.examprep.question.model.QuestionContent.Option;
import com.examprep.question.model.QuestionContent.Solution;
import com.examprep.question.model.QuestionTranslation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ResultDtos {

    private ResultDtos() {
    }

    public enum ResultStatus {
        /** Submitted; evaluation is queued or running (usually a few seconds). Poll again. */
        EVALUATING,
        /** Scored, but the test hides results until its window closes. */
        AWAITING_PUBLICATION,
        READY
    }

    /**
     * Result page. When {@code status != READY}, only the identifying fields are set.
     *
     * @param rankFinal false means rank and percentile are live (from the leaderboard) and may still change
     */
    public record ResultDto(UUID attemptId, UUID testId, String testTitle, ResultStatus status, int attemptNo,
                            boolean ranked, BigDecimal score, BigDecimal maxScore, BigDecimal percentage,
                            Integer rank, BigDecimal percentile, boolean rankFinal, Long totalCandidates,
                            Integer correct, Integer incorrect, Integer partial, Integer unattempted,
                            BigDecimal accuracy, Integer timeTakenSeconds, Instant evaluatedAt,
                            List<SectionResultDto> sections, List<TopicResultDto> topics,
                            boolean solutionsAvailable, Instant solutionsAvailableAt) {
    }

    public record SectionResultDto(UUID sectionId, String name, UUID subjectId, int total, int attempted,
                                   int correct, int incorrect, int partial, int unattempted, BigDecimal score,
                                   BigDecimal maxScore, BigDecimal accuracy, int timeSpentSeconds) {
    }

    public record TopicResultDto(UUID topicId, String topicName, String chapterName, String subjectName, int total,
                                 int attempted, int correct, int incorrect, int partial, BigDecimal score,
                                 BigDecimal maxScore, BigDecimal accuracy, int timeSpentSeconds) {
    }

    public record ReviewItemDto(int number, UUID questionId, QuestionType type, UUID paragraphId, String text,
                                List<Media> images, List<Option> options, List<MatchItem> matchLeft,
                                List<MatchItem> matchRight, StudentAnswer yourAnswer, AnswerState state,
                                AnswerKey correctAnswer, String outcome, BigDecimal marksAwarded, BigDecimal marks,
                                BigDecimal negativeMarks, int timeSpentSeconds, Solution solution,
                                Language language, Map<Language, QuestionTranslation> translations) {
    }

    public record ReviewSectionDto(UUID sectionId, String name, List<ReviewItemDto> questions) {
    }

    /** Questions in canonical order (not the student's shuffled order) with answers and solutions. */
    public record SolutionReviewDto(UUID attemptId, UUID testId, List<ReviewSectionDto> sections,
                                    Map<UUID, PassageView> passages) {
    }

    /** Names are shortened ("Riya S.") for privacy. {@code you} marks the caller's own entry. */
    public record LeaderboardEntryDto(int rank, String name, BigDecimal score, int timeTakenSeconds, boolean you) {
    }

    public record LeaderboardDto(UUID testId, long totalCandidates, List<LeaderboardEntryDto> entries,
                                 LeaderboardEntryDto me, BigDecimal myPercentile) {
    }

    public record PerformanceDto(BigDecimal score, BigDecimal accuracy, Integer timeTakenSeconds, BigDecimal correct,
                                 BigDecimal incorrect) {
    }

    public record SectionComparisonDto(UUID sectionId, String name, BigDecimal you, BigDecimal topper,
                                       BigDecimal average, BigDecimal maxScore) {
    }

    /** "You vs topper vs average" for one test. */
    public record ComparisonDto(UUID attemptId, UUID testId, long candidates, PerformanceDto you,
                                PerformanceDto topper, PerformanceDto average, List<SectionComparisonDto> sections) {
    }

    public record AdminResultRowDto(UUID resultId, UUID attemptId, UUID userId, String studentName, BigDecimal score,
                                    BigDecimal maxScore, Integer rank, BigDecimal percentile, boolean ranked,
                                    BigDecimal accuracy, int timeTakenSeconds, Instant evaluatedAt) {
    }
}
