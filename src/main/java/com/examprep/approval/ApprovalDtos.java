package com.examprep.approval;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ApprovalDtos {

    private ApprovalDtos() {
    }

    public enum Status { PENDING, APPROVED, REJECTED, EXECUTED, FAILED, CANCELLED, EXPIRED }

    public record ApprovalRequestDto(UUID id, String action, String actionDescription, String entityType,
                                     String entityId, String title, JsonNode payload, String reason, Status status,
                                     UUID requestedBy, String requestedByName, Instant requestedAt,
                                     Instant expiresAt, UUID decidedBy, String decidedByName, Instant decidedAt,
                                     String decisionComment, Instant executedAt, JsonNode result, String error,
                                     boolean canDecide, boolean canCancel) {
    }

    /** Body of a 202 response: the operation was not performed yet, it awaits a second person. */
    public record ApprovalPending(boolean approvalRequired, ApprovalRequestDto request, String message) {
    }

    public record PolicyDto(String action, boolean enabled, BigDecimal threshold, String approvePermission,
                            int expiryHours, String description, Instant updatedAt) {
    }

    public record UpdatePolicyRequest(boolean enabled, @DecimalMin("0") BigDecimal threshold,
                                      @Min(1) @Max(720) int expiryHours) {
    }

    public record DecisionRequest(@Size(max = 2000) String comment) {
    }

    public record RejectRequest(@NotBlank @Size(max = 2000) String comment) {
    }

    public record ApprovalSummary(long waitingForMe, long myPending) {
    }
}
