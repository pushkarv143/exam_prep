package com.examprep.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Create/update payloads for catalog nodes. Business codes and parent links are
 * <b>immutable</b> after creation, because questions and imports reference them. Update
 * requests therefore carry only the mutable fields.
 */
public final class CatalogRequests {

    private static final String CODE_REGEX = "^[A-Za-z][A-Za-z0-9_]{1,31}$";
    private static final String CODE_MESSAGE = "must be 2-32 letters, digits or underscores, starting with a letter";

    private CatalogRequests() {
    }

    public record CreateExamRequest(
            @NotBlank @Pattern(regexp = CODE_REGEX, message = CODE_MESSAGE) String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 5000) String description,
            @Min(0) @Max(10_000) int displayOrder) {
    }

    public record CreateSubjectRequest(
            @NotNull UUID examId,
            @NotBlank @Pattern(regexp = CODE_REGEX, message = CODE_MESSAGE) String code,
            @NotBlank @Size(max = 150) String name,
            @Min(0) @Max(10_000) int displayOrder) {
    }

    public record CreateChapterRequest(
            @NotNull UUID subjectId,
            @NotBlank @Size(max = 200) String name,
            @Min(0) @Max(10_000) int displayOrder) {
    }

    public record CreateTopicRequest(
            @NotNull UUID chapterId,
            @NotBlank @Size(max = 200) String name,
            @Min(0) @Max(10_000) int displayOrder) {
    }

    /** Shared by every level. {@code description} is ignored for levels that have none. */
    public record UpdateNodeRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 5000) String description,
            @Min(0) @Max(10_000) int displayOrder,
            boolean active) {
    }
}
