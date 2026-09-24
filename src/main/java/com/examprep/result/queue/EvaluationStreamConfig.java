package com.examprep.result.queue;

import com.examprep.result.EvaluationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamReadRequest;

import java.time.Duration;

/** Subscribes this instance to {@code stream:evaluation} as one consumer of group {@code evaluators}. */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "app.evaluation", name = "mode", havingValue = "STREAM", matchIfMissing = true)
public class EvaluationStreamConfig {

    @Bean(destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> evaluationStreamContainer(
            RedisConnectionFactory connectionFactory, EvaluationStream stream, EvaluationConsumer consumer,
            EvaluationProperties props) {
        SimpleAsyncTaskExecutor pollExecutor = new SimpleAsyncTaskExecutor("eval-stream-");
        pollExecutor.setVirtualThreads(true);

        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(1))
                        .batchSize(props.batchSize())
                        .executor(pollExecutor)
                        .errorHandler(t -> onPollError(t, stream))
                        .build();
        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        try {
            stream.ensureGroup();
        } catch (RuntimeException e) {
            log.error("Could not create consumer group ({}). It is retried on poll errors, and the DB sweep "
                    + "keeps evaluating meanwhile", rootMessage(e));
        }
        container.register(StreamReadRequest.builder(StreamOffset.create(EvaluationStream.STREAM, ReadOffset.lastConsumed()))
                        .consumer(Consumer.from(EvaluationStream.GROUP, stream.consumerName))
                        .autoAcknowledge(false)
                        .cancelOnError(t -> false)      // a Redis blip must not silently end the subscription
                        .build(),
                consumer);
        container.start();
        log.info("Evaluation consumer {} subscribed to {}", stream.consumerName, EvaluationStream.STREAM);
        return container;
    }

    /**
     * The container re-polls immediately after an error. Without a pause, a Redis outage
     * becomes a hot loop that burns CPU and floods the logs. This handler runs on the poll
     * thread, so sleeping here is the backoff. It also self-heals a lost consumer group
     * (NOGROUP after a Redis flush or failover to an empty replica).
     */
    private static void onPollError(Throwable t, EvaluationStream stream) {
        String detail = rootMessage(t);
        log.warn("Evaluation stream poll failed: {}", detail);
        if (detail.contains("NOGROUP")) {
            try {
                stream.ensureGroup();
                log.info("Recreated evaluation consumer group");
                return;
            } catch (RuntimeException e) {
                log.warn("Could not recreate consumer group: {}", rootMessage(e));
            }
        }
        try {
            Thread.sleep(POLL_ERROR_BACKOFF);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final Duration POLL_ERROR_BACKOFF = Duration.ofSeconds(5);

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
