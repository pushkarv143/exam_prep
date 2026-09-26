package com.examprep.audit.service;

import com.examprep.common.util.Uuids;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.TreeSet;

/**
 * Appends to the immutable audit log. Each write runs in its own transaction
 * (REQUIRES_NEW), so an audited operation that rolls back is still recorded, and a
 * failed audit write never breaks the business operation. It is logged loudly instead:
 * audit loss must be visible in monitoring.
 */
@Slf4j
@Service
public class AuditService {

    private static final int MAX_TEXT = 4000;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final Clock clock;

    public AuditService(JdbcTemplate jdbc, ObjectMapper json, PlatformTransactionManager txManager, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Converts any object (DTO, map) into a masked JSON tree suitable for storage. */
    public JsonNode snapshot(Object value) {
        if (value == null) {
            return null;
        }
        JsonNode node = value instanceof JsonNode n ? n.deepCopy() : json.valueToTree(value);
        return SensitiveData.mask(node);
    }

    public void record(AuditRecord r) {
        try {
            JsonNode before = SensitiveData.mask(r.before());
            JsonNode after = SensitiveData.mask(r.after());
            JsonNode changes = before != null || after != null ? JsonDiff.diff(before, after) : null;
            tx.executeWithoutResult(status -> jdbc.update("""
                    insert into audit_log (id, occurred_at, actor_id, actor_email, actor_roles, action, entity_type,
                        entity_id, outcome, http_method, path, status_code, error_code, reason, before, after, changes,
                        metadata, ip, user_agent, request_id)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?)
                    """,
                    Uuids.v7(), Timestamp.from(clock.instant()), r.actorId(), r.actorEmail(),
                    r.actorRoles() == null ? null : String.join(",", new TreeSet<>(r.actorRoles())),
                    cut(r.action(), 96), cut(r.entityType(), 48), cut(r.entityId(), 64), r.outcome().name(),
                    r.httpMethod(), cut(r.path(), 512), r.statusCode(), cut(r.errorCode(), 64), cut(r.reason(), MAX_TEXT),
                    toJson(before), toJson(after), toJson(changes), toJson(SensitiveData.mask(r.metadata())),
                    cut(r.ip(), 64), cut(r.userAgent(), 512), cut(r.requestId(), 64)));
        } catch (RuntimeException e) {
            log.error("AUDIT WRITE FAILED for action={} entity={}:{} actor={}", r.action(), r.entityType(),
                    r.entityId(), r.actorId(), e);
        }
    }

    private String toJson(JsonNode node) {
        if (node == null || node.isNull() || (node.isContainerNode() && node.isEmpty())) {
            return null;
        }
        try {
            return json.writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
