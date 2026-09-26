package com.examprep.jobs;

import com.examprep.common.api.ApiResponse;
import com.examprep.common.api.PageResponse;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.NotFoundException;
import com.examprep.rbac.service.PermissionService;
import com.examprep.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Background job monitor. Anyone with admin access sees their own jobs (for example an
 * export they started). {@code job.view} shows everyone's, and {@code job.manage} can retry
 * or cancel anyone's.
 */
@Tag(name = "Jobs (admin)")
@RestController
@RequestMapping("/api/v1/admin/jobs")
@RequiredArgsConstructor
@PreAuthorize("@perm.has('admin.access')")
public class AdminJobController {

    private final JobService jobs;
    private final PermissionService permissions;

    @Operation(summary = "List jobs (own jobs unless job.view)")
    @GetMapping
    public ApiResponse<PageResponse<JobDtos.JobDto>> list(@AuthenticationPrincipal AuthUser user,
                                                          @RequestParam(required = false) JobStatus status,
                                                          @RequestParam(required = false) String type,
                                                          @RequestParam(defaultValue = "false") boolean mine,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "25") int size) {
        boolean all = permissions.has(user, "job.view") && !mine;
        return ApiResponse.ok(jobs.search(status, type, all ? null : user.id(), Math.max(0, page),
                Math.max(1, Math.min(size, 100))));
    }

    @Operation(summary = "Get a job with its progress")
    @GetMapping("/{jobId}")
    public ApiResponse<JobDtos.JobDto> get(@AuthenticationPrincipal AuthUser user, @PathVariable UUID jobId) {
        return ApiResponse.ok(visible(user, jobId, "job.view"));
    }

    @Operation(summary = "Cancel a queued or running job")
    @PostMapping("/{jobId}/cancel")
    public ApiResponse<JobDtos.JobDto> cancel(@AuthenticationPrincipal AuthUser user, @PathVariable UUID jobId) {
        visible(user, jobId, "job.manage");
        return ApiResponse.ok(jobs.cancel(jobId));
    }

    @Operation(summary = "Retry a failed, dead or cancelled job")
    @PostMapping("/{jobId}/retry")
    public ApiResponse<JobDtos.JobDto> retry(@AuthenticationPrincipal AuthUser user, @PathVariable UUID jobId) {
        visible(user, jobId, "job.manage");
        return ApiResponse.ok(jobs.retry(jobId));
    }

    @Operation(summary = "Download the job's output file")
    @GetMapping("/{jobId}/artifact")
    public ResponseEntity<byte[]> artifact(@AuthenticationPrincipal AuthUser user, @PathVariable UUID jobId) {
        visible(user, jobId, "job.view");
        JobDtos.Artifact a = jobs.artifact(jobId).orElseThrow(() -> NotFoundException.of("Job artifact", jobId));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(a.filename(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(a.contentType()))
                .body(a.data());
    }

    /** The owner may always act on their own job; others need {@code permission}. */
    private JobDtos.JobDto visible(AuthUser user, UUID jobId, String permission) {
        JobDtos.JobDto job = jobs.get(jobId);
        if (!user.id().equals(job.createdBy()) && !permissions.has(user, permission)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return job;
    }
}
