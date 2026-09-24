package com.examprep.common.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Minimal distributed lock: {@code SET key token NX PX ttl} to acquire, and a Lua
 * compare-and-delete to release, so a holder whose lock expired can never delete
 * someone else's lock.
 *
 * <p>This is a <em>mutual-exclusion hint</em>, not a correctness guarantee (no fencing
 * tokens). Every critical section guarded by it also takes a DB row lock or relies on an
 * idempotent state check. The Redis lock only stops instances from piling up on the same work.
 *
 * <pre>
 * try (RedisLock.Handle h = lock.tryAcquire("lock:attempt:" + id, Duration.ofSeconds(30)).orElseThrow(...)) {
 *     ...
 * }
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLock {

    private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    public Optional<Handle> tryAcquire(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean ok = redis.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(ok) ? Optional.of(new Handle(key, token)) : Optional.empty();
    }

    public final class Handle implements AutoCloseable {

        private final String key;
        private final String token;

        private Handle(String key, String token) {
            this.key = key;
            this.token = token;
        }

        @Override
        public void close() {
            try {
                redis.execute(RELEASE, List.of(key), token);
            } catch (RuntimeException e) {
                // It will expire on its own; never fail the business operation on release.
                log.warn("Failed to release lock {}: {}", key, e.getMessage());
            }
        }
    }
}
