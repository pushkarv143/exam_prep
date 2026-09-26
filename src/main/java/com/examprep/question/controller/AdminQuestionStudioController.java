package com.examprep.question.controller;

import com.examprep.common.api.ApiResponse;
import com.examprep.question.dto.QuestionDto;
import com.examprep.question.service.QuestionService;
import com.examprep.question.workflow.ContentSettingsService;
import com.examprep.question.workflow.QuestionWorkflowService;
import com.examprep.question.workflow.StudioDtos.ActivityDto;
import com.examprep.question.workflow.StudioDtos.ApproveRequest;
import com.examprep.question.workflow.StudioDtos.AssignRequest;
import com.examprep.question.workflow.StudioDtos.BulkRequest;
import com.examprep.question.workflow.StudioDtos.BulkResult;
import com.examprep.question.workflow.StudioDtos.ChangesRequest;
import com.examprep.question.workflow.StudioDtos.CommentRequest;
import com.examprep.question.workflow.StudioDtos.ContentSettings;
import com.examprep.question.workflow.StudioDtos.PublishOutcome;
import com.examprep.question.workflow.StudioDtos.PublishRequest;
import com.examprep.question.workflow.StudioDtos.PublishResult;
import com.examprep.question.workflow.StudioDtos.QueueCounts;
import com.examprep.question.workflow.StudioDtos.RestoreVersionRequest;
import com.examprep.question.workflow.StudioDtos.ReviewerDto;
import com.examprep.question.workflow.StudioDtos.SubmitRequest;
import com.examprep.question.workflow.StudioDtos.VersionDetailDto;
import com.examprep.question.workflow.StudioDtos.VersionDto;
import com.examprep.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Content studio: review workflow, comments, version history and queues. Workflow calls
 * return the updated question, so the studio can re-render from one response.
 */
@Tag(name = "Content studio (admin)")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("@perm.has('admin.access')")
public class AdminQuestionStudioController {

    private static final String Q = "/questions/{id}";

    private final QuestionService questions;
    private final QuestionWorkflowService workflow;
    private final ContentSettingsService settings;

    // ------------------------------------------------------------------ workflow

    @Operation(summary = "Submit for review", description = "Picks the least-loaded eligible reviewer when "
            + "assigneeId is omitted and auto-assign is on; a resubmission goes back to the previous reviewer.")
    @PreAuthorize("@perm.has('question.update')")
    @PostMapping(Q + "/submit")
    public ApiResponse<QuestionDto> submit(@PathVariable UUID id, @Valid @RequestBody(required = false) SubmitRequest req,
                                           @AuthenticationPrincipal AuthUser user) {
        workflow.submit(id, req == null ? null : req.assigneeId(), req == null ? null : req.note(), user);
        return ApiResponse.ok(questions.get(id, user));
    }

    @Operation(summary = "Take a review yourself")
    @PreAuthorize("@perm.has('question.review')")
    @PostMapping(Q + "/claim")
    public ApiResponse<QuestionDto> claim(@PathVariable UUID id, @AuthenticationPrincipal AuthUser user) {
        workflow.claim(id, user);
        return ApiResponse.ok(questions.get(id, user));
    }

    @Operation(summary = "Assign (or unassign with null) the reviewer")
    @PreAuthorize("@perm.has('question.approve')")
    @PostMapping(Q + "/assign")
    public ApiResponse<QuestionDto> assign(@PathVariable UUID id, @RequestBody AssignRequest req,
                                           @AuthenticationPrincipal AuthUser user) {
        workflow.assign(id, req.assigneeId(), user);
        return ApiResponse.ok(questions.get(id, user));
    }

    @Operation(summary = "Eligible reviewers for the question's subject, least loaded first")
    @PreAuthorize("@perm.any('question.update', 'question.review', 'question.approve')")
    @GetMapping(Q + "/reviewers")
    public ApiResponse<List<ReviewerDto>> reviewers(@PathVariable UUID id) {
        return ApiResponse.ok(workflow.reviewersFor(id));
    }

    @Operation(summary = "Send back to the author with a comment")
    @PreAuthorize("@perm.has('question.review')")
    @PostMapping(Q + "/request-changes")
    public ApiResponse<QuestionDto> requestChanges(@PathVariable UUID id, @Valid @RequestBody ChangesRequest req,
                                                   @AuthenticationPrincipal AuthUser user) {
        workflow.requestChanges(id, req.comment(), user);
        return ApiResponse.ok(questions.get(id, user));
    }

    @Operation(summary = "Approve (optionally publish in the same step)",
            description = "Nobody approves a question they submitted or whose current version they saved.")
    @PreAuthorize("@perm.has('question.approve')")
    @PostMapping(Q + "/approve")
    public ApiResponse<PublishOutcome> approve(@PathVariable UUID id, @Valid @RequestBody ApproveRequest req,
                                               @AuthenticationPrincipal AuthUser user) {
        var published = workflow.approve(id, req.comment(), req.publish(), user);
        return ApiResponse.ok(result(questions.get(id, user), published.orElse(null)));
    }

    @Operation(summary = "Publish the current version",
            description = "Draft tests move to the new version. With propagate=true (default) published tests "
                    + "move too, but only when the new version scores every answer the same way (wording fixes).")
    @PreAuthorize("@perm.has('question.publish')")
    @PostMapping(Q + "/publish")
    public ApiResponse<PublishOutcome> publish(@PathVariable UUID id,
                                               @RequestBody(required = false) PublishRequest req,
                                               @AuthenticationPrincipal AuthUser user) {
        PublishResult r = workflow.publish(id, req == null ? null : req.propagate(), user);
        return ApiResponse.ok(result(questions.get(id, user), r));
    }

    @Operation(summary = "Restore an archived question")
    @PreAuthorize("@perm.has('question.archive')")
    @PostMapping(Q + "/restore")
    public ApiResponse<QuestionDto> restore(@PathVariable UUID id, @AuthenticationPrincipal AuthUser user) {
        workflow.restore(id, user);
        return ApiResponse.ok(questions.get(id, user));
    }

    @Operation(summary = "Apply one workflow action to many questions",
            description = "Each question succeeds or fails on its own; failures are listed with the reason.")
    @PreAuthorize("@perm.any('question.update', 'question.approve', 'question.publish', 'question.archive')")
    @PostMapping("/questions/bulk")
    public ApiResponse<BulkResult> bulk(@Valid @RequestBody BulkRequest req, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(workflow.bulk(req.action(), req.ids(), req.comment(), user));
    }

    // ------------------------------------------------------------------ comments & timeline

    @Operation(summary = "Timeline: workflow events and comments")
    @PreAuthorize("@perm.has('question.view')")
    @GetMapping(Q + "/activity")
    public ApiResponse<List<ActivityDto>> activity(@PathVariable UUID id) {
        return ApiResponse.ok(questions.timeline(id));
    }

    @Operation(summary = "Comment on a question, optionally about one field (e.g. options.B, solution)")
    @PreAuthorize("@perm.has('question.view')")
    @PostMapping(Q + "/comments")
    public ApiResponse<List<ActivityDto>> comment(@PathVariable UUID id, @Valid @RequestBody CommentRequest req,
                                                  @AuthenticationPrincipal AuthUser user) {
        workflow.comment(id, req.body(), req.field(), user);
        return ApiResponse.ok(questions.timeline(id));
    }

    @Operation(summary = "Mark a comment resolved (or reopen it with resolved=false)")
    @PreAuthorize("@perm.has('question.view')")
    @PostMapping(Q + "/comments/{commentId}/resolve")
    public ApiResponse<List<ActivityDto>> resolve(@PathVariable UUID id, @PathVariable UUID commentId,
                                                  @RequestParam(defaultValue = "true") boolean resolved,
                                                  @AuthenticationPrincipal AuthUser user) {
        workflow.resolveComment(id, commentId, resolved, user);
        return ApiResponse.ok(questions.timeline(id));
    }

    // ------------------------------------------------------------------ versions

    @Operation(summary = "Version history, newest first")
    @PreAuthorize("@perm.has('question.view')")
    @GetMapping(Q + "/versions")
    public ApiResponse<List<VersionDto>> versions(@PathVariable UUID id) {
        return ApiResponse.ok(questions.versions(id));
    }

    @Operation(summary = "One version with its full snapshot (for diffs)")
    @PreAuthorize("@perm.has('question.view')")
    @GetMapping(Q + "/versions/{version}")
    public ApiResponse<VersionDetailDto> version(@PathVariable UUID id, @PathVariable int version) {
        return ApiResponse.ok(questions.version(id, version));
    }

    @Operation(summary = "Roll back: save the content of an older version as a new version")
    @PreAuthorize("@perm.has('question.update')")
    @PostMapping(Q + "/versions/{version}/restore")
    public ApiResponse<QuestionDto> restoreVersion(@PathVariable UUID id, @PathVariable int version,
                                                   @Valid @RequestBody(required = false) RestoreVersionRequest req,
                                                   @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(questions.restoreVersion(id, version, req == null ? null : req.note(), user));
    }

    // ------------------------------------------------------------------ queues & settings

    @Operation(summary = "Counts for the review-queue tabs")
    @PreAuthorize("@perm.has('question.view')")
    @GetMapping("/questions/queues")
    public ApiResponse<QueueCounts> queues(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(workflow.counts(user));
    }

    @Operation(summary = "Content workflow settings")
    @PreAuthorize("@perm.has('question.view')")
    @GetMapping("/content/settings")
    public ApiResponse<ContentSettings> settings() {
        return ApiResponse.ok(settings.get());
    }

    @Operation(summary = "Change content workflow settings")
    @PreAuthorize("@perm.has('settings.manage')")
    @PutMapping("/content/settings")
    public ApiResponse<ContentSettings> updateSettings(@Valid @RequestBody ContentSettings req,
                                                       @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(settings.update(req, user.id()));
    }

    private static PublishOutcome result(QuestionDto question, PublishResult published) {
        return new PublishOutcome(question, published);
    }
}
