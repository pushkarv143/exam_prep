package com.examprep.result.queue;

import com.examprep.attempt.service.AttemptEvaluationAccess;
import com.examprep.result.EvaluationProperties;
import com.examprep.result.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Safety nets for the evaluation queue, run every minute on every instance:
 * <ol>
 *   <li><b>Stuck messages.</b> Pending entries idle beyond {@code reclaimIdle} (e.g. their
 *       consumer crashed) are XCLAIMed and re-processed. After {@code maxDeliveries} they
 *       are dead-lettered.</li>
 *   <li><b>Lost messages.</b> SUBMITTED attempts older than {@code sweepAfter} are
 *       evaluated directly from Postgres (Redis flushed, publish failed, ASYNC-mode crash).</li>
 *   <li>The stream is trimmed so it cannot grow without bound.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvaluationRecoveryJob {

    private final StringRedisTemplate redis;
    private final EvaluationStream stream;
    private final EvaluationConsumer consumer;
    private final EvaluationService evaluation;
    private final AttemptEvaluationAccess attempts;
    private final EvaluationProperties props;
    private final Clock clock;

    @Scheduled(fixedDelayString = "PT60S", initialDelayString = "PT30S")
    public void recover() {
        if (props.mode() == EvaluationProperties.Mode.STREAM) {
            try {
                reclaimPending();
                stream.trim();
            } catch (RuntimeException e) {
                log.warn("Stream recovery skipped: {}", e.getMessage());
            }
        }
        sweepDatabase();
    }

    void reclaimPending() {
        PendingMessages pending = redis.opsForStream().pending(EvaluationStream.STREAM, EvaluationStream.GROUP,
                Range.unbounded(), 200);
        for (PendingMessage p : pending) {
            if (p.getElapsedTimeSinceLastDelivery().compareTo(props.reclaimIdle()) < 0) {
                continue;
            }
            List<MapRecord<String, Object, Object>> claimed = redis.opsForStream().claim(EvaluationStream.STREAM,
                    EvaluationStream.GROUP, stream.consumerName,
                    XClaimOptions.minIdle(props.reclaimIdle()).ids(p.getId()));
            for (MapRecord<String, Object, Object> raw : claimed) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                MapRecord<String, String, String> record = (MapRecord) raw;
                if (p.getTotalDeliveryCount() >= props.maxDeliveries()) {
                    stream.deadLetter(record, "Failed " + p.getTotalDeliveryCount() + " deliveries");
                } else {
                    consumer.process(record);
                }
            }
        }
    }

    void sweepDatabase() {
        Instant cutoff = Instant.now(clock).minus(props.sweepAfter());
        List<UUID> stale = attempts.findSubmittedBefore(cutoff, 200);
        if (stale.isEmpty()) {
            return;
        }
        log.info("Evaluation sweep: {} submitted attempts were never evaluated; evaluating now", stale.size());
        for (UUID id : stale) {
            try {
                evaluation.evaluate(id, false);
            } catch (RuntimeException e) {
                log.error("Sweep evaluation of {} failed: {}", id, e.getMessage());
            }
        }
    }
}
