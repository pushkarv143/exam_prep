package com.examprep.question.workflow;

import com.examprep.common.util.Uuids;
import com.examprep.question.workflow.StudioDtos.ActivityDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The per-question timeline: workflow events plus reviewer comments ({@code question_activity}). */
@Service
@RequiredArgsConstructor
public class QuestionActivityService {

    public enum Kind {
        CREATED, EDITED, COMMENT, SUBMITTED, ASSIGNED, CHANGES_REQUESTED, APPROVED, PUBLISHED, ARCHIVED, RESTORED,
        ROLLED_BACK, OVERDUE
    }

    /** A comment row needed for permission checks. */
    public record CommentRef(UUID id, UUID questionId, UUID authorId, String kind, boolean resolved) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public UUID log(UUID questionId, Integer version, UUID actorId, Kind kind, String body, String field,
                    Map<String, ?> meta) {
        UUID id = Uuids.v7();
        jdbc.update("""
                insert into question_activity (id, question_id, version_no, actor_id, kind, body, field, meta, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """, id, questionId, version, actorId, kind.name(), blankToNull(body), blankToNull(field),
                meta == null || meta.isEmpty() ? null : write(meta), Timestamp.from(clock.instant()));
        return id;
    }

    public List<ActivityDto> timeline(UUID questionId) {
        return jdbc.query("""
                select a.id, a.version_no, a.actor_id, au.full_name, a.kind, a.body, a.field, a.resolved_at,
                       ru.full_name, a.meta::text, a.created_at
                from question_activity a
                left join users au on au.id = a.actor_id
                left join users ru on ru.id = a.resolved_by
                where a.question_id = ?
                order by a.created_at, a.id
                """, (rs, i) -> new ActivityDto(rs.getObject(1, UUID.class), (Integer) rs.getObject(2),
                rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toInstant(), rs.getString(9),
                read(rs.getString(10)), rs.getTimestamp(11).toInstant()), questionId);
    }

    public Optional<CommentRef> comment(UUID questionId, UUID commentId) {
        return jdbc.query("""
                select id, question_id, actor_id, kind, resolved_at is not null from question_activity
                where id = ? and question_id = ? and kind in ('COMMENT', 'CHANGES_REQUESTED')
                """, (rs, i) -> new CommentRef(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getString(4), rs.getBoolean(5)), commentId, questionId)
                .stream().findFirst();
    }

    public void resolve(UUID commentId, UUID userId, boolean resolved) {
        jdbc.update("update question_activity set resolved_at = ?, resolved_by = ? where id = ?",
                resolved ? Timestamp.from(clock.instant()) : null, resolved ? userId : null, commentId);
    }

    public int openComments(UUID questionId) {
        Integer n = jdbc.queryForObject("""
                select count(*) from question_activity
                where question_id = ? and kind in ('COMMENT', 'CHANGES_REQUESTED') and resolved_at is null
                """, Integer.class, questionId);
        return n == null ? 0 : n;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private String write(Object v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode read(String json) {
        try {
            return json == null ? null : objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
