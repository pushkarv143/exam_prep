package com.examprep.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Catalogue of machine-readable error codes returned in {@code ApiError.code}.
 * The frontend switches on these values, so treat them as part of the API contract.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ---- generic ----
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Bad request"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not allowed"),
    CONFLICT(HttpStatus.CONFLICT, "Request conflicts with current state"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "Resource already exists"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Resource was modified concurrently, please retry"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file is too large"),
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "File type is not allowed"),
    STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "File storage is temporarily unavailable"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests, please slow down"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong, please try again"),

    // ---- tests / enrollment / payment ----
    TEST_NOT_EDITABLE(HttpStatus.CONFLICT, "Only DRAFT tests can be structurally edited"),
    TEST_NOT_PUBLISHABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Test is not ready to be published"),
    ACCESS_DENIED_NOT_ENROLLED(HttpStatus.FORBIDDEN, "Enroll in the test series to access this test"),
    PAYMENT_REQUIRED(HttpStatus.PAYMENT_REQUIRED, "This test series requires payment"),
    PAYMENT_VERIFICATION_FAILED(HttpStatus.BAD_REQUEST, "Payment could not be verified"),
    PAYMENT_GATEWAY_ERROR(HttpStatus.BAD_GATEWAY, "Payment provider is unavailable, please retry"),

    // ---- attempts ----
    TEST_NOT_AVAILABLE(HttpStatus.CONFLICT, "This test is not open right now"),
    NO_ATTEMPTS_LEFT(HttpStatus.CONFLICT, "You have used all attempts for this test"),
    ATTEMPT_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "This attempt has already been submitted"),
    ATTEMPT_EXPIRED(HttpStatus.CONFLICT, "Time is up for this attempt"),
    SUBMIT_IN_PROGRESS(HttpStatus.CONFLICT, "Submission is already in progress, please retry in a moment"),

    // ---- auth ----
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Access token expired"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Invalid or revoked token"),
    SESSION_REVOKED(HttpStatus.UNAUTHORIZED, "Your session has ended, please log in again"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email/phone or password"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Your account is disabled, please contact support"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this action"),
    // ---- admin security (Admin Portal 2.0) ----
    MFA_INVALID_CODE(HttpStatus.UNAUTHORIZED, "The verification code is incorrect or has expired"),
    MFA_CHALLENGE_EXPIRED(HttpStatus.UNAUTHORIZED, "Your sign-in attempt expired, please log in again"),
    MFA_ENROLLMENT_REQUIRED(HttpStatus.FORBIDDEN, "Set up two-factor authentication to use the admin portal"),
    MFA_ALREADY_ENABLED(HttpStatus.CONFLICT, "Two-factor authentication is already enabled"),
    MFA_NOT_ENABLED(HttpStatus.CONFLICT, "Two-factor authentication is not enabled"),
    IP_NOT_ALLOWED(HttpStatus.FORBIDDEN, "The admin portal cannot be used from this network"),
    ROLE_READ_ONLY(HttpStatus.CONFLICT, "This built-in role cannot be changed this way"),
    ROLE_IN_USE(HttpStatus.CONFLICT, "The role is still assigned to users"),
    PRIVILEGE_ESCALATION(HttpStatus.FORBIDDEN, "You cannot grant permissions you do not have yourself"),
    APPROVAL_NOT_PENDING(HttpStatus.CONFLICT, "This request is no longer waiting for a decision"),
    APPROVAL_SELF_DECISION(HttpStatus.FORBIDDEN, "You cannot approve or reject your own request"),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "This operation requires an Idempotency-Key header"),
    IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT, "The same request is still being processed"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_ENTITY, "This Idempotency-Key was already used for a different request"),

    // Content studio (A2)
    QUESTION_VERSION_CONFLICT(HttpStatus.CONFLICT, "Someone saved a newer version of this question"),
    QUESTION_WORKFLOW(HttpStatus.CONFLICT, "This action is not possible in the question's current state"),
    QUESTION_SELF_REVIEW(HttpStatus.FORBIDDEN, "You cannot review or approve a question you submitted or edited"),
    REVIEWER_NOT_ELIGIBLE(HttpStatus.BAD_REQUEST, "This person cannot review questions of this subject");

    private final HttpStatus status;
    private final String defaultMessage;
}
