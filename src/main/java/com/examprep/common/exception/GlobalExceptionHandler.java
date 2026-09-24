package com.examprep.common.exception;

import com.examprep.common.api.ApiError.FieldViolation;
import com.examprep.common.api.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Translates exceptions into the standard {@link ApiResponse} error envelope.
 * Expected failures ({@link BusinessException}) are logged at DEBUG, unexpected
 * ones at ERROR with a stack trace. Internal details never reach the client.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ApiErrorFactory errors;

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(ex.getErrorCode().getStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(errors.build(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        log.debug("Business exception: {} - {}", ex.getErrorCode(), ex.getMessage());
        return respond(ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException ex) {
        List<FieldViolation> violations = ex.getBindingResult().getAllErrors().stream()
                .map(err -> new FieldViolation(
                        err instanceof FieldError fe ? fe.getField() : err.getObjectName(),
                        err.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(errors.build(ErrorCode.VALIDATION_FAILED, null, violations));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException ex) {
        List<FieldViolation> violations = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new FieldViolation(r.getMethodParameter().getParameterName(),
                                e.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest()
                .body(errors.build(ErrorCode.VALIDATION_FAILED, null, violations));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(errors.build(ErrorCode.VALIDATION_FAILED, null, violations));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            PropertyReferenceException.class})
    public ResponseEntity<ApiResponse<Void>> handleMalformed(Exception ex) {
        String message = switch (ex) {
            case MissingServletRequestParameterException m -> "Missing required parameter: " + m.getParameterName();
            case MissingServletRequestPartException m -> "Missing required part: " + m.getRequestPartName();
            case MethodArgumentTypeMismatchException t -> "Invalid value for parameter: " + t.getName();
            case PropertyReferenceException p -> "Unknown sort property: " + p.getPropertyName();
            default -> "Malformed request body";
        };
        return respond(ErrorCode.BAD_REQUEST, message);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(Exception ex) {
        return respond(ErrorCode.FORBIDDEN, null);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return respond(ErrorCode.CONCURRENT_MODIFICATION, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleIntegrity(DataIntegrityViolationException ex) {
        // Usually a unique-constraint race that the service pre-check did not catch.
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return respond(ErrorCode.CONFLICT, "The request conflicts with existing data");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadSize(MaxUploadSizeExceededException ex) {
        return respond(ErrorCode.PAYLOAD_TOO_LARGE, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return respond(ErrorCode.NOT_FOUND, "No endpoint " + ex.getResourcePath());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(ErrorCode.INTERNAL_ERROR, null);
    }

    private ResponseEntity<ApiResponse<Void>> respond(ErrorCode code, String message) {
        return ResponseEntity.status(code.getStatus()).body(errors.build(code, message));
    }
}
