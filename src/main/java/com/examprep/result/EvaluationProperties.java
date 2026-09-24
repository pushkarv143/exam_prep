package com.examprep.result;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * {@code app.evaluation.*}
 *
 * @param mode           STREAM (Redis Stream + consumer group, the default) or ASYNC (in-process @Async,
 *                       for single-node setups or Redis servers without stream support)
 * @param reclaimIdle    pending stream messages idle longer than this are re-processed by the recovery job
 * @param maxDeliveries  after this many failed deliveries a message goes to the dead-letter stream
 * @param sweepAfter     SUBMITTED attempts older than this are evaluated by the DB sweep (lost messages)
 * @param finalizeDelay  final ranking waits this long after a test's end time (auto-submit grace + evaluation)
 * @param finalizeForceAfter  after this, ranks are finalised even if some attempts are still unfinished
 */
@ConfigurationProperties(prefix = "app.evaluation")
public record EvaluationProperties(
        @DefaultValue("STREAM") Mode mode,
        @DefaultValue("20") int batchSize,
        /* parallel evaluations per instance; keep below the DB pool size */
        @DefaultValue("16") int concurrency,
        @DefaultValue("60s") Duration reclaimIdle,
        @DefaultValue("5") int maxDeliveries,
        @DefaultValue("2m") Duration sweepAfter,
        @DefaultValue("10m") Duration specCacheTtl,
        @DefaultValue("2m") Duration finalizeDelay,
        @DefaultValue("30m") Duration finalizeForceAfter) {

    public enum Mode { STREAM, ASYNC }
}
