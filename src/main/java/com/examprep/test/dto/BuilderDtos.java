package com.examprep.test.dto;

import com.examprep.question.entity.Difficulty;
import com.examprep.question.entity.Language;
import com.examprep.question.entity.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Payloads of the test builder: sections, question placement, ordering, auto-generation. */
public final class BuilderDtos {

    private BuilderDtos() {
    }

    public record SectionRequest(
            @NotBlank @Size(max = 150) String name,
            UUID subjectId,
            @Size(max = 10_000) String instructions,
            @Min(0) int displayOrder,
            @DecimalMin("0") @DecimalMax("100") BigDecimal defaultMarks,
            @DecimalMin("0") @DecimalMax("100") BigDecimal defaultNegativeMarks,
            @Positive Integer maxQuestionsToAttempt,
            QuestionType questionType,
            @Positive @Max(500) Integer targetCount) {
    }

    /**
     * Add bank questions to a section, in the given order. PARAGRAPH ids expand to their
     * ACTIVE child questions. Marks override the section and question defaults.
     */
    public record AddQuestionsRequest(
            @NotEmpty @Size(max = 200) List<@NotNull UUID> questionIds,
            @DecimalMin("0") @DecimalMax("100") BigDecimal marks,
            @DecimalMin("0") @DecimalMax("100") BigDecimal negativeMarks,
            Boolean partialMarking) {
    }

    public record AddQuestionsResult(int added, List<UUID> skippedAlreadyInTest, List<UUID> expandedParagraphs) {
    }

    public record UpdateTestQuestionRequest(
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal marks,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal negativeMarks,
            boolean partialMarking) {
    }

    /** Drag-and-drop result: every test-question id of the section, in the new order. */
    public record ReorderRequest(@NotEmpty List<@NotNull UUID> testQuestionIds) {
    }

    /**
     * One selection rule. It fills {@code sectionId} with {@code count} random questions
     * of the section's subject. The optional filters narrow the pool.
     */
    public record GenerationRule(
            @NotNull UUID sectionId,
            @Positive @Max(200) int count,
            Difficulty difficulty,
            Set<UUID> chapterIds,
            Set<UUID> topicIds,
            Set<QuestionType> types,
            Language language) {
    }

    /**
     * @param strict fail (and add nothing) if any rule cannot be fully satisfied;
     *               otherwise add what was found and report the shortfall
     */
    public record GenerateRequest(@NotEmpty @Size(max = 100) List<@Valid GenerationRule> rules, boolean strict,
                                  boolean excludeUsedInPublishedTests) {
    }

    /** Fill every pattern section up to its target count, with a difficulty mix (percentages sum to 100). */
    public record GenerateFromPatternRequest(
            @Min(0) @Max(100) int easyPercent,
            @Min(0) @Max(100) int mediumPercent,
            @Min(0) @Max(100) int hardPercent,
            Language language,
            boolean strict,
            boolean excludeUsedInPublishedTests) {
    }

    public record Shortfall(UUID sectionId, String sectionName, String rule, int requested, int found) {
    }

    public record GenerationReport(int added, List<Shortfall> shortfalls) {
    }
}
