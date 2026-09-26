package com.examprep.audit.service;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Lets service code enrich the audit row of the current HTTP request:
 * <pre>
 * AuditContext.action("test.publish");
 * AuditContext.entity("TEST", id);
 * AuditContext.change(beforeDto, afterDto);   // snapshots; the diff is computed on write
 * </pre>
 * {@code AuditFilter} writes one row per audited request after it completes, so a failed call
 * is still logged (outcome FAILURE). Outside a request (jobs, schedulers) calls are no-ops;
 * code there uses {@link AuditService#record} directly.
 */
public final class AuditContext {

    public static final String ATTRIBUTE = AuditContext.class.getName();

    private AuditContext() {
    }

    public static void action(String action) {
        capture().ifPresent(c -> c.action = action);
    }

    public static void entity(String type, Object id) {
        capture().ifPresent(c -> {
            c.entityType = type;
            c.entityId = id == null ? null : id.toString();
        });
    }

    /** Records before/after snapshots (any Jackson-serialisable objects, usually DTOs). */
    public static void change(Object before, Object after) {
        capture().ifPresent(c -> {
            if (c.before == null) {
                c.before = before;   // keep the earliest "before" if called twice
            }
            c.after = after;
        });
    }

    public static void meta(String key, Object value) {
        capture().ifPresent(c -> c.metadata.put(key, value));
    }

    /** Audits this request even if it is not an admin write (e.g. 2FA changes under /me). */
    public static void force() {
        capture().ifPresent(c -> c.forced = true);
    }

    static Optional<Capture> capture() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return Optional.empty();
        }
        Object c = attrs.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return Optional.ofNullable((Capture) c);
    }

    /** Mutable per-request state, created by {@code AuditFilter}. */
    public static final class Capture {
        String action;
        String entityType;
        String entityId;
        Object before;
        Object after;
        boolean forced;
        final Map<String, Object> metadata = new LinkedHashMap<>();

        public String action() { return action; }
        public String entityType() { return entityType; }
        public String entityId() { return entityId; }
        public Object before() { return before; }
        public Object after() { return after; }
        public boolean forced() { return forced; }
        public Map<String, Object> metadata() { return metadata; }
    }
}
