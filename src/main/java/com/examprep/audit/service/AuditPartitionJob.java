package com.examprep.audit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps monthly audit partitions created three months ahead (the SQL function is idempotent,
 * so every instance may run it). Rows only fall into {@code audit_log_default} if this job
 * stopped for months.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditPartitionJob {

    private final JdbcTemplate jdbc;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        ensure();
    }

    @Scheduled(cron = "0 17 3 * * *")
    public void ensure() {
        try {
            Integer made = jdbc.queryForObject("select audit_ensure_partitions(current_date, 3)", Integer.class);
            if (made != null && made > 0) {
                log.info("Created {} audit log partition(s)", made);
            }
        } catch (RuntimeException e) {
            log.error("Could not create audit partitions", e);
        }
    }
}
