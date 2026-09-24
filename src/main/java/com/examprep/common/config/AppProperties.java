package com.examprep.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * General application settings bound from the {@code app.*} namespace.
 * Security settings live in {@code AppSecurityProperties}.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("http://localhost:5173") String frontendUrl,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue Notification notification) {

    public record RateLimit(@DefaultValue("true") boolean enabled) {
    }

    public record Notification(@DefaultValue Email email) {

        public record Email(@DefaultValue("true") boolean enabled,
                            @DefaultValue("ExamPrep <no-reply@examprep.local>") String from) {
        }
    }
}
