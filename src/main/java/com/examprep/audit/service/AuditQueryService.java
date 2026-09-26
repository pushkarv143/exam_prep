package com.examprep.audit.service;

import com.examprep.audit.dto.AuditDtos;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Searches the audit log with keyset pagination on {@code (occurred_at, id)} descending,
 * served by {@code ix_audit_time} and the per-filter indexes. Offsets are never used, so
 * page 1,000 is as fast as page 1 at any table size.
 */
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    public static final Duration DEFAULT_RANGE = Duration.ofDays(30);
    private static final Duration MAX_RANGE = Duration.ofDays(366);

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public AuditDtos.AuditPage search(AuditDtos.AuditFilter f, String cursor, int size) {
        List<Object> args = new ArrayList<>();
        String where = where(f, args);
        if (cursor != null && !cursor.isBlank()) {
            Cursor c = Cursor.decode(cursor);
            where += " and (occurred_at, id) < (?, ?)";
            args.add(Timestamp.from(c.at()));
            args.add(c.id());
        }
        args.add(size + 1);
        List<AuditDtos.AuditEntryDto> rows = jdbc.query(
                "select * from audit_log" + where + " order by occurred_at desc, id desc limit ?", mapper(),
                args.toArray());
        String next = null;
        if (rows.size() > size) {
            rows = rows.subList(0, size);
            AuditDtos.AuditEntryDto last = rows.get(size - 1);
            next = new Cursor(last.occurredAt(), last.id()).encode();
        }
        return new AuditDtos.AuditPage(rows, next);
    }

    public AuditDtos.AuditEntryDto get(UUID id) {
        return jdbc.query("select * from audit_log where id = ?", mapper(), id).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Audit entry not found"));
    }

    /** Streams matching rows (newest first) to {@code handler}, at most {@code limit}. */
    public void stream(AuditDtos.AuditFilter f, int limit, RowCallbackHandler handler) {
        List<Object> args = new ArrayList<>();
        String where = where(f, args);
        args.add(limit);
        jdbc.query(con -> {
            var ps = con.prepareStatement(
                    "select * from audit_log" + where + " order by occurred_at desc, id desc limit ?");
            ps.setFetchSize(1000);
            for (int i = 0; i < args.size(); i++) {
                ps.setObject(i + 1, args.get(i));
            }
            return ps;
        }, handler);
    }

    public RowMapper<AuditDtos.AuditEntryDto> mapper() {
        return (rs, i) -> new AuditDtos.AuditEntryDto(
                rs.getObject("id", UUID.class), rs.getTimestamp("occurred_at").toInstant(),
                rs.getObject("actor_id", UUID.class), rs.getString("actor_email"), rs.getString("actor_roles"),
                rs.getString("action"), rs.getString("entity_type"), rs.getString("entity_id"),
                rs.getString("outcome"), rs.getString("http_method"), rs.getString("path"),
                rs.getObject("status_code", Integer.class), rs.getString("error_code"),
                rs.getString("reason"), read(rs.getString("before")), read(rs.getString("after")),
                read(rs.getString("changes")), read(rs.getString("metadata")), rs.getString("ip"),
                rs.getString("user_agent"), rs.getString("request_id"));
    }

    private String where(AuditDtos.AuditFilter f, List<Object> args) {
        Instant to = f.to() != null ? f.to() : clock.instant().plus(1, ChronoUnit.MINUTES);
        Instant from = f.from() != null ? f.from() : to.minus(DEFAULT_RANGE);
        if (!from.isBefore(to)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "'from' must be before 'to'");
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "The time range can be at most one year");
        }
        StringBuilder w = new StringBuilder(" where occurred_at >= ? and occurred_at < ?");
        args.add(Timestamp.from(from));
        args.add(Timestamp.from(to));
        if (f.actorId() != null) {
            w.append(" and actor_id = ?");
            args.add(f.actorId());
        }
        if (notBlank(f.actorEmail())) {
            w.append(" and actor_email ilike ?");
            args.add("%" + like(f.actorEmail()) + "%");
        }
        if (notBlank(f.action())) {
            // "test." matches every test action; a full code matches itself (and longer codes)
            w.append(" and action like ?");
            args.add(like(f.action()) + "%");
        }
        if (notBlank(f.entityType())) {
            w.append(" and entity_type = ?");
            args.add(f.entityType().trim().toUpperCase());
        }
        if (notBlank(f.entityId())) {
            w.append(" and entity_id = ?");
            args.add(f.entityId().trim());
        }
        if (notBlank(f.outcome())) {
            w.append(" and outcome = ?");
            args.add(f.outcome().trim().toUpperCase());
        }
        if (notBlank(f.q())) {
            w.append(" and (action ilike ? or path ilike ? or reason ilike ? or entity_id = ?)");
            String q = "%" + like(f.q()) + "%";
            args.add(q);
            args.add(q);
            args.add(q);
            args.add(f.q().trim());
        }
        return w.toString();
    }

    private JsonNode read(String s) {
        if (s == null) {
            return null;
        }
        try {
            return json.readTree(s);
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Escapes LIKE wildcards in user input (PostgreSQL's default escape character is a backslash). */
    private static String like(String s) {
        return s.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private record Cursor(Instant at, UUID id) {
        String encode() {
            String raw = at.getEpochSecond() + ":" + at.getNano() + ":" + id;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        static Cursor decode(String s) {
            try {
                String[] p = new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8).split(":", 3);
                return new Cursor(Instant.ofEpochSecond(Long.parseLong(p[0]), Long.parseLong(p[1])),
                        UUID.fromString(p[2]));
            } catch (RuntimeException e) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid cursor");
            }
        }
    }
}
