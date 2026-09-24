package com.examprep.common.config;

import com.examprep.security.SecurityUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Fills createdAt/updatedAt (UTC Instants) and createdBy/updatedBy (current user id). */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<UUID> auditorAware() {
        return SecurityUtils::currentUserId;
    }

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }
}
