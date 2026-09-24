package com.examprep.question.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Typed shape of {@code questions.content} (JSONB). Text fields may contain Markdown
 * with inline/display LaTeX ({@code $...$}, {@code $$...$$}). The frontend renders
 * them with KaTeX.
 *
 * <p>Using a record rather than a raw JsonNode means Bean Validation and the type
 * checks apply, and a typo'd field can never reach the student UI. Unknown JSON fields
 * are ignored, so older rows stay readable if the shape grows.
 *
 * <p>{@link #solution()} is an admin/review-time field. The test-paper builder
 * (Phase 4) maps to a separate student DTO that never includes it.
 */
public record QuestionContent(
        @Size(max = 20_000) String text,
        @Size(max = 10) List<@Valid Media> images,
        @Size(max = 6) List<@Valid Option> options,
        @Size(max = 20_000) String paragraph,
        @Size(max = 10) List<@Valid MatchItem> matchLeft,
        @Size(max = 10) List<@Valid MatchItem> matchRight,
        @Valid Solution solution) {

    public QuestionContent {
        images = images == null ? List.of() : List.copyOf(images);
        options = options == null ? List.of() : List.copyOf(options);
        matchLeft = matchLeft == null ? List.of() : List.copyOf(matchLeft);
        matchRight = matchRight == null ? List.of() : List.copyOf(matchRight);
    }

    /** One answer choice. The {@code id} is the label shown to students (A, B, C, D). */
    public record Option(@NotBlank @Size(max = 5) String id, @Size(max = 5_000) String text,
                         @Size(max = 1_000) String image) {
    }

    public record Media(@NotBlank @Size(max = 1_000) String url, @Size(max = 300) String alt) {
    }

    public record MatchItem(@NotBlank @Size(max = 5) String id, @NotBlank @Size(max = 2_000) String text) {
    }

    public record Solution(@Size(max = 20_000) String text, @Size(max = 1_000) String videoUrl,
                           @Size(max = 10) List<@Valid Media> images) {
    }
}
