package com.examprep.common.ratelimit;

import com.examprep.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisRateLimiterIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    RedisRateLimiter limiter;

    @Test
    void allows_up_to_limit_then_blocks_with_retry_after() {
        String key = "test:" + UUID.randomUUID();
        for (int i = 1; i <= 3; i++) {
            RedisRateLimiter.Result r = limiter.tryAcquire(key, 3, 60);
            assertThat(r.allowed()).isTrue();
            assertThat(r.remaining()).isEqualTo(3 - i);
        }
        RedisRateLimiter.Result blocked = limiter.tryAcquire(key, 3, 60);
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isBetween(1L, 60L);
    }

    @Test
    void separate_keys_have_independent_budgets() {
        String a = "test:" + UUID.randomUUID();
        String b = "test:" + UUID.randomUUID();
        assertThat(limiter.tryAcquire(a, 1, 60).allowed()).isTrue();
        assertThat(limiter.tryAcquire(a, 1, 60).allowed()).isFalse();
        assertThat(limiter.tryAcquire(b, 1, 60).allowed()).isTrue();
    }
}
