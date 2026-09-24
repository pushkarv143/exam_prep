package com.examprep.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Error payload of {@link ApiResponse}.
 *
 * @param code       stable, machine-readable error code (see {@code ErrorCode})
 * @param message    human-readable message, safe to show to end users
 * @param violations field-level validation errors (only for VALIDATION_FAILED)
 * @param requestId  correlation id, also returned in the {@code X-Request-Id} header
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String code, String message, List<FieldViolation> violations, String requestId) {

    public record FieldViolation(String field, String message) {
    }
}
