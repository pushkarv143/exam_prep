package com.examprep.question.service;

import com.examprep.audit.service.JsonDiff;
import com.examprep.question.dto.QuestionPin;
import com.examprep.question.entity.Question;
import com.examprep.question.model.QuestionSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable question versions ({@code question_versions}). Every save of a question writes
 * one row holding the full {@link QuestionSnapshot}; tests reference versions, never the
 * mutable working copy.
 */
@Service
@RequiredArgsConstructor
public class QuestionVersionService {

    private static final int BATCH = 400;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final Clock clock;

    /** One row of the version history, without the snapshot. */
    public record VersionInfo(int version, List<String> changedFields, String changeNote, Integer restoredFrom,
                              UUID createdBy, String createdByName, Instant createdAt, Instant publishedAt,
                              String publishedByName) {
    }

    /**
     * Writes the next version of {@code q} (already modified in memory) and advances
     * {@code q.currentVersion}. Flushes the question first, because the version row
     * references it.
     *
     * @return the new version number
     */
    public int record(Question q, QuestionSnapshot previous, UUID userId, String note, Integer restoredFrom) {
        QuestionSnapshot snapshot = QuestionSnapshot.of(q);
        int next = q.getCurrentVersion() + 1;
        q.setCurrentVersion(next);
        entityManager.flush();
        jdbc.update("""
                insert into question_versions (question_id, version_no, snapshot, changed_fields, change_note,
                                               restored_from, created_by, created_at)
                values (?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?)
                """, q.getId(), next, write(snapshot), write(changedFields(previous, snapshot)),
                note == null || note.isBlank() ? null : note.trim(), restoredFrom, userId,
                Timestamp.from(clock.instant()));
        return next;
    }

    /** Version rows for a batch of freshly imported questions (all version 1). */
    public void recordInitial(Collection<Question> questions, UUID userId, String note, boolean published) {
        Timestamp now = Timestamp.from(clock.instant());
        List<Object[]> rows = new ArrayList<>(questions.size());
        for (Question q : questions) {
            rows.add(new Object[]{q.getId(), q.getCurrentVersion(), write(QuestionSnapshot.of(q)), note, userId, now,
                    published ? now : null, published ? userId : null});
        }
        jdbc.batchUpdate("""
                insert into question_versions (question_id, version_no, snapshot, change_note, created_by, created_at,
                                               published_at, published_by)
                values (?, ?, ?::jsonb, ?, ?, ?, ?, ?)
                """, rows);
    }

    public void markPublished(UUID questionId, int version, UUID userId) {
        jdbc.update("""
                update question_versions set published_at = coalesce(published_at, ?), published_by = coalesce(published_by, ?)
                where question_id = ? and version_no = ?
                """, Timestamp.from(clock.instant()), userId, questionId, version);
    }

    /** Who saved the given version (the last editor, for the current version). */
    public Optional<UUID> author(UUID questionId, int version) {
        return jdbc.query("select created_by from question_versions where question_id = ? and version_no = ?",
                (rs, i) -> rs.getObject(1, UUID.class), questionId, version).stream()
                .filter(java.util.Objects::nonNull).findFirst();
    }

    public Optional<QuestionSnapshot> load(UUID questionId, int version) {
        return jdbc.query("select snapshot::text from question_versions where question_id = ? and version_no = ?",
                (rs, i) -> read(rs.getString(1)), questionId, version).stream().findFirst();
    }

    /** Snapshots of the given pins, keyed by question id. Missing versions are simply absent. */
    public Map<UUID, QuestionSnapshot> load(Collection<QuestionPin> pins) {
        Map<UUID, QuestionSnapshot> out = new HashMap<>();
        List<QuestionPin> list = new ArrayList<>(new LinkedHashSet<>(pins));
        for (int from = 0; from < list.size(); from += BATCH) {
            List<QuestionPin> chunk = list.subList(from, Math.min(list.size(), from + BATCH));
            StringBuilder values = new StringBuilder();
            List<Object> args = new ArrayList<>(chunk.size() * 2);
            for (QuestionPin p : chunk) {
                values.append(values.isEmpty() ? "" : ",").append("(?::uuid, ?)");
                args.add(p.questionId());
                args.add(p.version());
            }
            jdbc.query("select v.question_id, v.snapshot::text from question_versions v join (values " + values
                            + ") as p(qid, ver) on v.question_id = p.qid and v.version_no = p.ver",
                    rs -> {
                        out.put(rs.getObject(1, UUID.class), read(rs.getString(2)));
                    }, args.toArray());
        }
        return out;
    }

    public List<VersionInfo> history(UUID questionId) {
        return jdbc.query("""
                select v.version_no, v.changed_fields::text, v.change_note, v.restored_from, v.created_by,
                       cu.full_name as created_by_name, v.created_at, v.published_at, pu.full_name as published_by_name
                from question_versions v
                left join users cu on cu.id = v.created_by
                left join users pu on pu.id = v.published_by
                where v.question_id = ?
                order by v.version_no desc
                """, (rs, i) -> new VersionInfo(rs.getInt(1), readList(rs.getString(2)), rs.getString(3),
                (Integer) rs.getObject(4), rs.getObject(5, UUID.class), rs.getString(6),
                rs.getTimestamp(7).toInstant(), rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toInstant(),
                rs.getString(9)), questionId);
    }

    /** Top-level-ish paths that differ, e.g. {@code content.text}, {@code content.options}, {@code answerKey}. */
    List<String> changedFields(QuestionSnapshot before, QuestionSnapshot after) {
        if (before == null) {
            return List.of();
        }
        JsonNode changes = JsonDiff.diff(objectMapper.valueToTree(before), objectMapper.valueToTree(after));
        Set<String> paths = new LinkedHashSet<>();
        for (JsonNode c : changes) {
            String path = c.path("path").asText();
            String[] parts = path.split("\\.");
            String head = parts[0];
            // content.* and translations.HI keep one more level: "content.text", "translations.HI"
            if ((head.equals("content") || head.equals("translations")) && parts.length > 1) {
                paths.add(head + "." + parts[1]);
            } else {
                paths.add(head);
            }
        }
        return List.copyOf(paths);
    }

    public QuestionSnapshot read(String json) {
        try {
            return objectMapper.readValue(json, QuestionSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unreadable question snapshot: " + e.getOriginalMessage(), e);
        }
    }

    private List<String> readList(String json) {
        try {
            return json == null ? List.of() : List.of(objectMapper.readValue(json, String[].class));
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
