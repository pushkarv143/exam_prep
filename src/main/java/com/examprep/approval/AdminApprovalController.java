package com.examprep.approval;

import com.examprep.common.api.ApiResponse;
import com.examprep.common.api.PageResponse;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.idempotency.Idempotent;
import com.examprep.rbac.service.PermissionService;
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

@Tag(name = "Approvals (maker-checker)")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminApprovalController {

    private final ApprovalService approvals;
    private final PermissionService permissions;

    @Operation(summary = "List approval requests", description = "view = to-decide | mine | all")
    @PreAuthorize("@perm.has('admin.access')")
    @GetMapping("/approvals")
    public ApiResponse<PageResponse<ApprovalDtos.ApprovalRequestDto>> list(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam(defaultValue = "to-decide") String view,
            @RequestParam(required = false) ApprovalDtos.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String v = "all".equals(view) && !canSeeAll(user) ? "mine" : view;
        return ApiResponse.ok(approvals.search(user, v, status, Math.max(0, page), Math.max(1, Math.min(size, 100))));
    }

    @Operation(summary = "Counts for the navigation badge")
    @PreAuthorize("@perm.has('admin.access')")
    @GetMapping("/approvals/summary")
    public ApiResponse<ApprovalDtos.ApprovalSummary> summary(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(approvals.summary(user));
    }

    @Operation(summary = "Get one approval request")
    @PreAuthorize("@perm.has('admin.access')")
    @GetMapping("/approvals/{id}")
    public ApiResponse<ApprovalDtos.ApprovalRequestDto> get(@AuthenticationPrincipal AuthUser user,
                                                            @PathVariable UUID id) {
        ApprovalDtos.ApprovalRequestDto req = approvals.get(id, user);
        if (!req.requestedBy().equals(user.id()) && !canSeeAll(user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return ApiResponse.ok(req);
    }

    @Operation(summary = "Approve and execute (send an Idempotency-Key header)")
    @PreAuthorize("@perm.has('approval.decide')")
    @Idempotent
    @PostMapping("/approvals/{id}/approve")
    public ApiResponse<ApprovalDtos.ApprovalRequestDto> approve(@AuthenticationPrincipal AuthUser user,
                                                                @PathVariable UUID id,
                                                                @Valid @RequestBody(required = false)
                                                                ApprovalDtos.DecisionRequest body) {
        return ApiResponse.ok(approvals.approve(id, user, body == null ? null : body.comment()));
    }

    @Operation(summary = "Reject with a mandatory comment")
    @PreAuthorize("@perm.has('approval.decide')")
    @Idempotent
    @PostMapping("/approvals/{id}/reject")
    public ApiResponse<ApprovalDtos.ApprovalRequestDto> reject(@AuthenticationPrincipal AuthUser user,
                                                               @PathVariable UUID id,
                                                               @Valid @RequestBody ApprovalDtos.RejectRequest body) {
        return ApiResponse.ok(approvals.reject(id, user, body.comment()));
    }

    @Operation(summary = "Withdraw your own pending request")
    @PreAuthorize("@perm.has('admin.access')")
    @PostMapping("/approvals/{id}/cancel")
    public ApiResponse<ApprovalDtos.ApprovalRequestDto> cancel(@AuthenticationPrincipal AuthUser user,
                                                               @PathVariable UUID id) {
        return ApiResponse.ok(approvals.cancel(id, user));
    }

    @Operation(summary = "Approval policies (which actions need a second person)")
    @PreAuthorize("@perm.any('security.manage', 'approval.view')")
    @GetMapping("/approval-policies")
    public ApiResponse<List<ApprovalDtos.PolicyDto>> policies() {
        return ApiResponse.ok(approvals.policies());
    }

    @Operation(summary = "Change an approval policy")
    @PreAuthorize("@perm.has('security.manage')")
    @PutMapping("/approval-policies/{action}")
    public ApiResponse<ApprovalDtos.PolicyDto> updatePolicy(@AuthenticationPrincipal AuthUser user,
                                                            @PathVariable String action,
                                                            @Valid @RequestBody ApprovalDtos.UpdatePolicyRequest body) {
        return ApiResponse.ok(approvals.updatePolicy(action, body, user));
    }

    private boolean canSeeAll(AuthUser user) {
        return permissions.has(user, "approval.view") || permissions.has(user, "approval.decide");
    }
}
