package com.examprep.result.queue;

import com.examprep.result.EvaluationProperties;
import com.examprep.result.service.EvaluationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Handles evaluation messages with bounded parallelism. The container's poll thread
 * blocks on the semaphore when all workers are busy, which is natural backpressure:
 * unread messages simply wait in the stream.
 */
@Slf4j
@Component
public class EvaluationConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final EvaluationService evaluation;
    private final EvaluationStream stream;
    private final Semaphore permits;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

    public EvaluationConsumer(EvaluationService evaluation, EvaluationStream stream, EvaluationProperties props) {
        this.evaluation = evaluation;
        this.stream = stream;
        this.permits = new Semaphore(Math.max(1, props.concurrency()));
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        permits.acquireUninterruptibly();
        workers.submit(() -> {
            try {
                process(message);
            } finally {
                permits.release();
            }
        });
    }

    /** Also used by the recovery job for claimed pending messages. */
    void process(MapRecord<String, String, String> message) {
        UUID attemptId;
        try {
            attemptId = UUID.fromString(message.getValue().get("attemptId"));
        } catch (RuntimeException e) {
            stream.deadLetter(message, "Unparseable payload");
            return;
        }
        try {
            evaluation.evaluate(attemptId, false);
            stream.ack(message.getId());
        } catch (RuntimeException e) {
            // Not acked: the message stays pending and the recovery job retries it.
            log.warn("Evaluation of attempt {} failed (message {} stays pending): {}", attemptId, message.getId(),
                    e.getMessage());
        }
    }
}
