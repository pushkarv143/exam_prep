package com.examprep.common.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fixed-window counter implemented as one atomic Lua script: INCR, set the expiry on
 * the first hit, return (count, ttl). That is one round trip per request, with no
 * race between INCR and EXPIRE.
 *
 * <p>A fixed window can let up to 2x the limit through at a window boundary. That is
 * fine for abuse protection on login/OTP/autosave. Switch to a sliding-log or GCRA
 * script if you need exact smoothing.
 *
 * <p>If Redis is unavailable the limiter <b>fails open</b>: a Redis outage must not lock
 * students out of a live exam.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRateLimiter {

    private static final RedisScript<List> SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
              ttl = tonumber(ARGV[1])
            end
            return {current, ttl}
            """, List.class);

    private final StringRedisTemplate redis;

    public Result tryAcquire(String key, int limit, long windowSeconds) {
        try {
            @SuppressWarnings("unchecked")
            List<Long> res = redis.execute(SCRIPT, List.of("rl:" + key), String.valueOf(windowSeconds * 1000));
            long count = res.get(0);
            long ttlMs = res.get(1);
            long remaining = Math.max(0, limit - count);
            long retryAfter = Math.max(1, (ttlMs + 999) / 1000);
            return new Result(count <= limit, limit, remaining, retryAfter);
        } catch (RuntimeException e) {
            log.warn("Rate limiter unavailable, allowing request (key={}): {}", key, e.getMessage());
            return new Result(true, limit, limit, 0);
        }
    }

    public record Result(boolean allowed, int limit, long remaining, long retryAfterSeconds) {
    }
}
