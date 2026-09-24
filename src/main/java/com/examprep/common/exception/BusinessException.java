package com.examprep.common.exception;

import lombok.Getter;

/**
 * Base class for all expected, domain-level failures. {@code GlobalExceptionHandler}
 * turns it into an {@code ApiResponse} with the status that belongs to the
 * {@link ErrorCode}. Services throw this (or a subclass) and never build HTTP
 * responses themselves.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
