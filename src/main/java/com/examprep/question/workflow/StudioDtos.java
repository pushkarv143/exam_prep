package com.examprep.question.workflow;

import com.examprep.question.model.QuestionSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request and response types of the content studio (workflow, comments, versions, queues). */
public final class StudioDtos {

    private StudioDtos() {
    }

    // ------------------------------------------------------------------ workflow

    /** @param assigneeId reviewer to assign; null lets the system pick (when auto-assign is on) */
    public record SubmitRequest(UUID assigneeId, @Size(max = 2_000) String note) {
    }

    /** @param assigneeId null unassigns */
    public record AssignRequest(UUID assigneeId) {
    }

    public record ChangesRequest(@NotBlank @Size(max = 5_000) String comment) {
    }

    /** @param publish also publish right away (needs question.publish) */
    public record ApproveRequest(@Size(max = 5_000) String comment, boolean publish) {
    }

    /**
     * @param propagate also move <b>published</b> tests to the new version when it scores
     *                  every answer the same way (wording fixes). Draft tests always move.
     */
    public record PublishRequest(Boolean propagate) {
    }

    /**
     * @param draftTestsUpdated     draft tests now using the new version
     * @param liveTestsUpdated      published tests now using the new version (wording-only change)
     * @param liveTestsKept         published tests that keep their old version (the answer key,
     *                              type or options changed, or propagation was not requested)
     */
    public record PublishResult(int version, Integer previousVersion, int draftTestsUpdated, int liveTestsUpdated,
                                int liveTestsKept) {
    }

    /** Result of approve/publish: the updated question and, if something was published, what moved. */
    public record PublishOutcome(com.examprep.question.dto.QuestionDto question, PublishResult published) {
    }

    // ------------------------------------------------------------------ comments & activity

    public record CommentRequest(@NotBlank @Size(max = 5_000) String body, @Size(max = 80) String field) {
    }

    public record ActivityDto(UUID id, Integer version, UUID actorId, String actorName, String kind, String body,
                              String field, Instant resolvedAt, String resolvedByName, JsonNode meta,
                              Instant createdAt) {
    }

    // ------------------------------------------------------------------ versions

    public record VersionDto(int version, List<String> changedFields, String changeNote, Integer restoredFrom,
                             UUID createdBy, String createdByName, Instant createdAt, Instant publishedAt,
                             String publishedByName, boolean live, boolean current) {
    }

    public record VersionDetailDto(VersionDto info, QuestionSnapshot snapshot) {
    }

    public record RestoreVersionRequest(@Size(max = 500) String note) {
    }

    // ------------------------------------------------------------------ queues

    public record ReviewerDto(UUID id, String name, String email, long openReviews) {
    }

    /**
     * Tab counts of the review queue.
     *
     * @param changesRequested questions of the caller sent back for changes
     * @param approved         approved revisions waiting to be published
     */
    public record QueueCounts(long assignedToMe, long unassigned, long overdue, long inReview, long changesRequested,
                              long approved, long myDrafts) {
    }

    public enum BulkAction { SUBMIT, APPROVE, PUBLISH, ARCHIVE }

    public record BulkRequest(@NotNull BulkAction action, @NotEmpty @Size(max = 200) List<@NotNull UUID> ids,
                              @Size(max = 2_000) String comment) {
    }

    public record BulkFailure(UUID id, String message) {
    }

    public record BulkResult(int succeeded, List<BulkFailure> failed) {
    }

    // ------------------------------------------------------------------ settings

    public record ContentSettings(boolean reviewRequired, @Min(1) @Max(720) int slaHours, boolean autoAssign) {
    }
}
