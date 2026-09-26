package com.examprep.test.dto;

import com.examprep.question.dto.QuestionSummaryDto;
import com.examprep.question.entity.QuestionType;
import com.examprep.test.entity.ExamPattern;
import com.examprep.test.entity.TestAvailability;
import com.examprep.test.entity.TestStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TestDtos {

    private TestDtos() {
    }

    /**
     * @param pattern         non-CUSTOM patterns create their sections automatically
     * @param durationMinutes defaults to the pattern's duration (required for CUSTOM)
     */
    public record CreateTestRequest(
            UUID seriesId,
            @NotNull UUID examId,
            @NotBlank @Size(max = 250) String title,
            @Size(max = 10_000) String description,
            @Size(max = 20_000) String instructions,
            @NotNull ExamPattern pattern,
            @Positive @Max(600) Integer durationMinutes,
            Instant startAt,
            Instant endAt,
            boolean free,
            boolean shuffleQuestions,
            boolean shuffleOptions,
            @Min(1) @Max(50) Integer maxAttempts,
            Boolean showResultImmediately,
            @Min(0) int displayOrder) {
    }

    /**
     * Full replacement of test settings. After publishing, only title, description,
     * instructions, free flag, display order, result visibility, and <em>extending</em>
     * endAt may change. Everything else would alter a test that students already see.
     */
    public record UpdateTestRequest(
            UUID seriesId,
            @NotBlank @Size(max = 250) String title,
            @Size(max = 10_000) String description,
            @Size(max = 20_000) String instructions,
            @NotNull @Positive @Max(600) Integer durationMinutes,
            Instant startAt,
            Instant endAt,
            boolean free,
            boolean shuffleQuestions,
            boolean shuffleOptions,
            @NotNull @Min(1) @Max(50) Integer maxAttempts,
            boolean showResultImmediately,
            @Min(0) int displayOrder) {
    }

    public record TestDto(UUID id, UUID seriesId, UUID examId, String title, String description, String instructions,
                          ExamPattern pattern, int durationMinutes, BigDecimal totalMarks, int totalQuestions,
                          Instant startAt, Instant endAt, TestStatus status, boolean free, boolean shuffleQuestions,
                          boolean shuffleOptions, int maxAttempts, boolean showResultImmediately, int displayOrder,
                          Instant ranksComputedAt, Instant createdAt, Instant updatedAt) {
    }

    /**
     * @param questionVersion the version this test uses; compare with {@code question.publishedVersion}
     *                        to offer "a newer version is available"
     */
    public record TestQuestionDto(UUID id, UUID questionId, int displayOrder, BigDecimal marks,
                                  BigDecimal negativeMarks, boolean partialMarking, int questionVersion,
                                  Integer passageVersion, QuestionSummaryDto question) {
    }

    public record SectionDto(UUID id, UUID subjectId, String name, String instructions, int displayOrder,
                             BigDecimal defaultMarks, BigDecimal defaultNegativeMarks, Integer maxQuestionsToAttempt,
                             QuestionType questionType, Integer targetCount, int questionCount,
                             BigDecimal sectionMarks, List<TestQuestionDto> questions) {
    }

    /** Admin test-builder view: the test plus every section and question. */
    public record TestDetailDto(TestDto test, List<SectionDto> sections) {
    }

    public record ValidationReport(boolean publishable, List<String> errors, List<String> warnings) {
    }

    /** Listing card on the public series page. {@code accessible} is null for anonymous visitors. */
    public record PublicTestDto(UUID id, String title, String description, ExamPattern pattern, int durationMinutes,
                                BigDecimal totalMarks, int totalQuestions, Instant startAt, Instant endAt,
                                TestAvailability availability, boolean free, int displayOrder, Boolean accessible) {
    }

    public record SectionInfo(String name, UUID subjectId, int questionCount, Integer maxQuestionsToAttempt,
                              BigDecimal marks) {
    }

    /** Pre-test page for a student: instructions, structure, and whether they may start. */
    public record TestInfoDto(UUID id, UUID seriesId, String seriesSlug, String title, String description,
                              String instructions, ExamPattern pattern, int durationMinutes, BigDecimal totalMarks,
                              int totalQuestions, Instant startAt, Instant endAt, TestAvailability availability,
                              boolean free, int maxAttempts, boolean hasAccess, List<SectionInfo> sections) {
    }

    public record PatternDto(ExamPattern code, String displayName, Integer defaultDurationMinutes,
                             int totalQuestions, BigDecimal totalMarks, List<ExamPattern.SectionTemplate> sections) {
    }
}
