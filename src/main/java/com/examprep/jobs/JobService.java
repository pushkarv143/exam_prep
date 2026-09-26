package com.examprep.jobs;

import com.examprep.common.api.PageResponse;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.exception.NotFoundException;
import com.examprep.common.util.Uuids;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Enqueue, inspect, cancel and retry background jobs. Execution lives in {@link JobWorker}.
 * The queue is the {@code jobs} table: durable, transactional with the enqueuing code, and
 * claimable by many instances with {@code FOR UPDATE SKIP LOCKED}.
 */
@Service
@RequiredArgsConstructor
public class JobService {

    private static final String SELECT = """
            select j.*, u.full_name as created_by_name,
                   a.filename as a_filename, a.content_type as a_content_type, a.size_bytes as a_size
            from jobs j
            left join users u on u.id = j.created_by
            left join job_artifacts a on a.job_id = j.id
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    /**
     * Queues a job. With an idempotency key, a second enqueue of the same type and key returns the
     * existing job instead of creating a duplicate.
     */
    public JobDtos.JobDto enqueue(String type, Object params, UUID createdBy, String idempotencyKey,
                                  int maxAttempts) {
        UUID id = Uuids.v7();
        try {
            jdbc.update("""
                    insert into jobs (id, type, params, max_attempts, created_by, idempotency_key)
                    values (?, ?, ?::jsonb, ?, ?, ?)
                    """, id, type, toJson(params), maxAttempts, createdBy, idempotencyKey);
        } catch (DuplicateKeyException e) {
            UUID existing = jdbc.queryForObject("select id from jobs where type = ? and idempotency_key = ?",
                    UUID.class, type, idempotencyKey);
            return get(existing);
        }
        return get(id);
    }

    public JobDtos.JobDto get(UUID id) {
        return find(id).orElseThrow(() -> NotFoundException.of("Job", id));
    }

    public Optional<JobDtos.JobDto> find(UUID id) {
        return jdbc.query(SELECT + " where j.id = ?", mapper(), id).stream().findFirst();
    }

    public PageResponse<JobDtos.JobDto> search(JobStatus status, String type, UUID createdBy, int page, int size) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" where 1=1");
        if (status != null) {
            where.append(" and j.status = ?");
            args.add(status.name());
        }
        if (type != null && !type.isBlank()) {
            where.append(" and j.type = ?");
            args.add(type.trim());
        }
        if (createdBy != null) {
            where.append(" and j.created_by = ?");
            args.add(createdBy);
        }
        Long total = jdbc.queryForObject("select count(*) from jobs j" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<JobDtos.JobDto> rows = jdbc.query(SELECT + where + " order by j.created_at desc limit ? offset ?",
                mapper(), pageArgs.toArray());
        long t = total == null ? 0 : total;
        int pages = (int) Math.ceil(t / (double) size);
        return new PageResponse<>(rows, page, size, t, pages, page >= pages - 1);
    }

    /** Queued jobs are cancelled at once; running ones are asked to stop at their next check. */
    public JobDtos.JobDto cancel(UUID id) {
        JobDtos.JobDto job = get(id);
        if (job.status().isFinal()) {
            throw new BusinessException(ErrorCode.CONFLICT, "The job has already finished");
        }
        int queued = jdbc.update("""
                update jobs set status = 'CANCELLED', cancel_requested = true, finished_at = now()
                where id = ? and status = 'QUEUED'
                """, id);
        if (queued == 0) {
            jdbc.update("update jobs set cancel_requested = true where id = ? and status = 'RUNNING'", id);
        }
        return get(id);
    }

    /** Re-queues a FAILED, DEAD or CANCELLED job with a fresh attempt budget. */
    public JobDtos.JobDto retry(UUID id) {
        int n = jdbc.update("""
                update jobs set status = 'QUEUED', attempts = 0, error = null, cancel_requested = false,
                       progress = 0, progress_message = null, run_after = now(), finished_at = null,
                       locked_by = null, locked_until = null
                where id = ? and status in ('FAILED', 'DEAD', 'CANCELLED')
                """, id);
        if (n == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "Only failed, dead or cancelled jobs can be retried");
        }
        return get(id);
    }

    public Optional<JobDtos.Artifact> artifact(UUID jobId) {
        return jdbc.query("select filename, content_type, data from job_artifacts where job_id = ?",
                (rs, i) -> new JobDtos.Artifact(rs.getString(1), rs.getString(2), rs.getBytes(3)), jobId)
                .stream().findFirst();
    }

    // ------------------------------------------------------------------ helpers

    String toJson(Object value) {
        try {
            return value == null ? "{}" : json.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalArgumentException("Job params are not serialisable", e);
        }
    }

    JsonNode readJson(String value) {
        if (value == null) {
            return null;
        }
        try {
            return json.readTree(value);
        } catch (IOException e) {
            return json.getNodeFactory().textNode(value);
        }
    }

    private RowMapper<JobDtos.JobDto> mapper() {
        return (rs, i) -> new JobDtos.JobDto(
                rs.getObject("id", UUID.class), rs.getString("type"), JobStatus.valueOf(rs.getString("status")),
                rs.getInt("progress"), rs.getString("progress_message"), readJson(rs.getString("params")),
                readJson(rs.getString("result")), rs.getString("error"), rs.getInt("attempts"),
                rs.getInt("max_attempts"), rs.getBoolean("cancel_requested"),
                rs.getObject("created_by", UUID.class), rs.getString("created_by_name"),
                instant(rs, "created_at"), instant(rs, "started_at"), instant(rs, "finished_at"),
                instant(rs, "run_after"),
                rs.getString("a_filename") == null ? null : new JobDtos.ArtifactDto(rs.getString("a_filename"),
                        rs.getString("a_content_type"), rs.getLong("a_size")));
    }

    private static java.time.Instant instant(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }
}
