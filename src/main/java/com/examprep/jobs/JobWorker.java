package com.examprep.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Claims and runs queued jobs.
 * <ul>
 *   <li>Claiming uses {@code UPDATE ... WHERE id IN (SELECT ... FOR UPDATE SKIP LOCKED)}, so
 *       any number of instances can poll without taking the same job twice.</li>
 *   <li>A claimed job holds a lease ({@code locked_until}). The worker renews it while the job
 *       runs. If an instance dies, the lease expires and the reaper re-queues the job.</li>
 *   <li>Failures retry with exponential backoff (10 s, 20 s, 40 s, ...) until
 *       {@code max_attempts}, then the job is DEAD (the dead-letter state, visible in the UI).</li>
 * </ul>
 */
@Slf4j
@Component
public class JobWorker {

    private static final Duration LEASE = Duration.ofSeconds(90);

    private final JdbcTemplate jdbc;
    private final JobService jobs;
    private final Map<String, JobHandler> handlers;
    private final Clock clock;
    private final int concurrency;
    private final boolean enabled;
    private final String workerId;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();

    public JobWorker(JdbcTemplate jdbc, JobService jobs, List<JobHandler> handlerBeans, Clock clock,
                     @Value("${app.jobs.concurrency:4}") int concurrency,
                     @Value("${app.jobs.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.jobs = jobs;
        this.handlers = handlerBeans.stream().collect(Collectors.toMap(JobHandler::type, Function.identity()));
        this.clock = clock;
        this.concurrency = concurrency;
        this.enabled = enabled;
        this.workerId = ManagementFactory.getRuntimeMXBean().getName() + "-" + UUID.randomUUID().toString()
                .substring(0, 8);
        log.info("Job worker {} ready for types {}", workerId, handlers.keySet());
    }

    @Scheduled(fixedDelayString = "${app.jobs.poll-interval:PT1S}", initialDelayString = "PT5S")
    public void poll() {
        if (!enabled) {
            return;
        }
        int free = concurrency - running.size();
        if (free <= 0) {
            return;
        }
        List<Claimed> claimed = jdbc.query("""
                update jobs set status = 'RUNNING', attempts = attempts + 1, locked_by = ?,
                       locked_until = now() + (? * interval '1 second'),
                       started_at = coalesce(started_at, now())
                where id in (
                    select id from jobs
                    where status = 'QUEUED' and run_after <= now() and type = any (?)
                    order by priority, run_after
                    limit ?
                    for update skip locked)
                returning id, type, params, created_by, attempts, max_attempts
                """, (rs, i) -> new Claimed(rs.getObject("id", UUID.class), rs.getString("type"),
                        jobs.readJson(rs.getString("params")), rs.getObject("created_by", UUID.class),
                        rs.getInt("attempts"), rs.getInt("max_attempts")),
                workerId, LEASE.toSeconds(), handlers.keySet().toArray(new String[0]), free);
        for (Claimed job : claimed) {
            running.add(job.id);
            executor.submit(() -> execute(job));
        }
    }

    /** Renews leases of jobs running here, and re-queues jobs whose lease expired elsewhere. */
    @Scheduled(fixedDelayString = "PT20S", initialDelayString = "PT20S")
    public void heartbeatAndReap() {
        if (!running.isEmpty()) {
            jdbc.update("update jobs set locked_until = now() + (? * interval '1 second') "
                    + "where id = any (?) and status = 'RUNNING'", LEASE.toSeconds(), running.toArray(new UUID[0]));
        }
        int requeued = jdbc.update("""
                update jobs set status = case when attempts >= max_attempts then 'DEAD' else 'QUEUED' end,
                       error = coalesce(error, 'Worker lease expired (instance stopped?)'),
                       locked_by = null, locked_until = null,
                       finished_at = case when attempts >= max_attempts then now() else null end
                where status = 'RUNNING' and locked_until < now()
                """);
        if (requeued > 0) {
            log.warn("Reaped {} jobs with expired leases", requeued);
        }
    }

    private void execute(Claimed job) {
        JobHandler handler = handlers.get(job.type);
        Context ctx = new Context(job);
        try {
            JsonNode result = handler.run(ctx);
            jdbc.update("""
                    update jobs set status = 'SUCCEEDED', progress = 100, result = ?::jsonb, error = null,
                           finished_at = now(), locked_by = null, locked_until = null
                    where id = ?
                    """, result == null ? null : result.toString(), job.id);
            log.info("Job {} ({}) succeeded", job.id, job.type);
        } catch (JobContext.CancelledException e) {
            jdbc.update("update jobs set status = 'CANCELLED', finished_at = now(), locked_by = null, "
                    + "locked_until = null where id = ?", job.id);
            log.info("Job {} ({}) cancelled", job.id, job.type);
        } catch (Throwable e) {
            boolean retryable = !(e instanceof JobContext.NonRetryableException) && job.attempts < job.maxAttempts;
            String error = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
            if (retryable) {
                long backoff = 10L * (1L << Math.min(job.attempts - 1, 10));
                jdbc.update("""
                        update jobs set status = 'QUEUED', error = ?, run_after = now() + (? * interval '1 second'),
                               locked_by = null, locked_until = null
                        where id = ?
                        """, cut(error), backoff, job.id);
                log.warn("Job {} ({}) failed attempt {}/{}; retrying in {}s: {}", job.id, job.type, job.attempts,
                        job.maxAttempts, backoff, error);
            } else {
                String status = e instanceof JobContext.NonRetryableException ? "FAILED" : "DEAD";
                jdbc.update("update jobs set status = ?, error = ?, finished_at = now(), locked_by = null, "
                        + "locked_until = null where id = ?", status, cut(error), job.id);
                log.error("Job {} ({}) {} after {} attempts", job.id, job.type, status, job.attempts, e);
            }
        } finally {
            running.remove(job.id);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private static String cut(String s) {
        return s.length() > 4000 ? s.substring(0, 4000) : s;
    }

    private record Claimed(UUID id, String type, JsonNode params, UUID createdBy, int attempts, int maxAttempts) {
    }

    /** Throttles DB writes: progress at most every 500 ms, cancel flag re-read every 2 s. */
    private final class Context implements JobContext {
        private final Claimed job;
        private Instant lastProgress = Instant.EPOCH;
        private Instant lastCancelCheck = Instant.EPOCH;
        private boolean cancelled;

        Context(Claimed job) {
            this.job = job;
        }

        @Override
        public UUID jobId() {
            return job.id;
        }

        @Override
        public JsonNode params() {
            return job.params;
        }

        @Override
        public UUID createdBy() {
            return job.createdBy;
        }

        @Override
        public void progress(int percent, String message) {
            Instant now = clock.instant();
            if (percent < 100 && Duration.between(lastProgress, now).toMillis() < 500) {
                return;
            }
            lastProgress = now;
            jdbc.update("update jobs set progress = ?, progress_message = ? where id = ?",
                    Math.max(0, Math.min(100, percent)), message == null ? null : message.substring(0,
                            Math.min(255, message.length())), job.id);
        }

        @Override
        public void checkCancelled() {
            Instant now = clock.instant();
            if (!cancelled && Duration.between(lastCancelCheck, now).toSeconds() >= 2) {
                lastCancelCheck = now;
                cancelled = Boolean.TRUE.equals(jdbc.queryForObject(
                        "select cancel_requested from jobs where id = ?", Boolean.class, job.id));
            }
            if (cancelled) {
                throw new CancelledException();
            }
        }

        @Override
        public void saveArtifact(String filename, String contentType, byte[] data) {
            jdbc.update("""
                    insert into job_artifacts (job_id, filename, content_type, size_bytes, data)
                    values (?, ?, ?, ?, ?)
                    on conflict (job_id) do update set filename = excluded.filename,
                        content_type = excluded.content_type, size_bytes = excluded.size_bytes,
                        data = excluded.data, created_at = now()
                    """, job.id, filename, contentType, (long) data.length, data);
        }
    }
}
