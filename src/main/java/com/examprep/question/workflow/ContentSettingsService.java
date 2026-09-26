package com.examprep.question.workflow;

import com.examprep.audit.service.AuditContext;
import com.examprep.question.workflow.StudioDtos.ContentSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Content workflow settings, stored in {@code system_settings}:
 * {@code content.review.required}, {@code content.review.sla-hours} and
 * {@code content.review.auto-assign}. Read on every workflow action (cheap primary-key lookups).
 */
@Service
@RequiredArgsConstructor
public class ContentSettingsService {

    static final String REQUIRED = "content.review.required";
    static final String SLA_HOURS = "content.review.sla-hours";
    static final String AUTO_ASSIGN = "content.review.auto-assign";

    private final JdbcTemplate jdbc;

    public ContentSettings get() {
        return new ContentSettings(bool(REQUIRED, true), (int) number(SLA_HOURS, 48), bool(AUTO_ASSIGN, true));
    }

    @Transactional
    public ContentSettings update(ContentSettings next, UUID userId) {
        ContentSettings before = get();
        put(REQUIRED, String.valueOf(next.reviewRequired()), userId);
        put(SLA_HOURS, String.valueOf(next.slaHours()), userId);
        put(AUTO_ASSIGN, String.valueOf(next.autoAssign()), userId);
        AuditContext.action("content.settings.update");
        AuditContext.entity("SETTINGS", "content.review");
        AuditContext.change(before, next);
        return get();
    }

    private void put(String key, String jsonValue, UUID userId) {
        jdbc.update("""
                insert into system_settings (key, value, updated_by, updated_at) values (?, ?::jsonb, ?, now())
                on conflict (key) do update set value = excluded.value, updated_by = excluded.updated_by,
                                                updated_at = excluded.updated_at
                """, key, jsonValue, userId);
    }

    private boolean bool(String key, boolean fallback) {
        String v = raw(key);
        return v == null ? fallback : Boolean.parseBoolean(v.trim());
    }

    private long number(String key, long fallback) {
        String v = raw(key);
        try {
            return v == null ? fallback : Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String raw(String key) {
        List<String> v = jdbc.queryForList("select value::text from system_settings where key = ?", String.class, key);
        return v.isEmpty() ? null : v.getFirst();
    }
}
