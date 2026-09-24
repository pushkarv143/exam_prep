package com.examprep.common.exception;

import lombok.Getter;

/** Raised by the rate limiter. The handler turns {@code retryAfterSeconds} into a {@code Retry-After} header. */
@Getter
public class RateLimitExceededException extends BusinessException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
