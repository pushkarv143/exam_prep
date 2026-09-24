package com.examprep.common.ratelimit;

import com.examprep.common.config.AppProperties;
import com.examprep.common.exception.RateLimitExceededException;
import com.examprep.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces {@link RateLimit} annotations. It runs after the Spring Security filter
 * chain, so the authenticated user is already available for USER-keyed limits.
 * Exceptions thrown here go through {@code GlobalExceptionHandler}, which returns 429
 * with a Retry-After header.
 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisRateLimiter limiter;
    private final AppProperties appProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!appProperties.rateLimit().enabled() || !(handler instanceof HandlerMethod method)) {
            return true;
        }
        RateLimit rule = method.getMethodAnnotation(RateLimit.class);
        if (rule == null) {
            return true;
        }

        String subject = switch (rule.key()) {
            case IP -> "ip:" + request.getRemoteAddr();
            case USER -> SecurityUtils.currentUserId()
                    .map(id -> "u:" + id)
                    .orElse("ip:" + request.getRemoteAddr());
        };

        RedisRateLimiter.Result result = limiter.tryAcquire(rule.name() + ":" + subject, rule.limit(),
                rule.windowSeconds());
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        if (!result.allowed()) {
            throw new RateLimitExceededException(result.retryAfterSeconds());
        }
        return true;
    }
}
