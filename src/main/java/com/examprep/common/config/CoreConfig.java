package com.examprep.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.Clock;

/**
 * Cross-cutting infrastructure beans.
 *
 * <p>{@code @Async} runs on Boot's auto-configured executor. With
 * {@code spring.threads.virtual.enabled=true} that executor uses virtual threads, so
 * blocking I/O (SMTP, S3) in async listeners is cheap.
 */
@Slf4j
@Configuration
@EnableAsync
public class CoreConfig implements AsyncConfigurer {

    /** Single time source. Inject it instead of calling Instant.now() so tests can control time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error("Async method {} failed", method.getName(), ex);
    }
}
