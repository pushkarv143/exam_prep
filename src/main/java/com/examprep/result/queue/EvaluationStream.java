package com.examprep.result.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.Map;
import java.util.UUID;

/**
 * The evaluation work queue: Redis Stream {@code stream:evaluation} with consumer group
 * {@code evaluators}.
 * <ul>
 *   <li>At-least-once delivery. Evaluation is idempotent, so redelivery is harmless.</li>
 *   <li>Each app instance is one named consumer, and messages are load-balanced across instances.</li>
 *   <li>Unacked messages stay in the PEL (pending entries list) and are retried by
 *       {@code EvaluationRecoveryJob}. Poison messages move to {@code stream:evaluation:dlq}.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvaluationStream {

    public static final String STREAM = "stream:evaluation";
    public static final String DLQ = "stream:evaluation:dlq";
    public static final String GROUP = "evaluators";
    static final long MAX_LENGTH = 100_000;

    private final StringRedisTemplate redis;

    /** Stable per process: host plus PID. */
    public final String consumerName = consumerName();

    public RecordId publish(UUID attemptId) {
        return redis.opsForStream().add(StreamRecords.newRecord().in(STREAM)
                .ofMap(Map.of("attemptId", attemptId.toString())));
    }

    /** Creates the group (and the stream) if missing. BUSYGROUP means it already exists. */
    public void ensureGroup() {
        try {
            redis.opsForStream().createGroup(STREAM, ReadOffset.from("0-0"), GROUP);
            log.info("Created consumer group {} on {}", GROUP, STREAM);
        } catch (RuntimeException e) {
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (String.valueOf(t.getMessage()).contains("BUSYGROUP")) {
                    return;   // already exists
                }
            }
            throw e;
        }
    }

    public void ack(RecordId id) {
        redis.opsForStream().acknowledge(STREAM, GROUP, id);
    }

    public void deadLetter(MapRecord<String, String, String> record, String reason) {
        redis.opsForStream().add(StreamRecords.newRecord().in(DLQ).ofMap(Map.of(
                "originalId", record.getId().getValue(),
                "payload", String.valueOf(record.getValue()),
                "reason", reason)));
        ack(record.getId());
        log.error("Evaluation message {} dead-lettered: {}", record.getId(), reason);
    }

    public void trim() {
        redis.opsForStream().trim(STREAM, MAX_LENGTH, true);
    }

    private static String consumerName() {
        try {
            return "eval-" + InetAddress.getLocalHost().getHostName() + "-" + ManagementFactory.getRuntimeMXBean().getPid();
        } catch (Exception e) {
            return "eval-" + UUID.randomUUID();
        }
    }
}
