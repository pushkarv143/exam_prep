package com.examprep.result.queue;

import com.examprep.attempt.event.AttemptSubmittedEvent;
import com.examprep.result.EvaluationProperties;
import com.examprep.result.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Queues evaluation once a submit has <b>committed</b> (the attempt's answers are in
 * Postgres). If Redis is unavailable, it degrades to in-process async evaluation.
 * The recovery job's DB sweep is the final safety net.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvaluationDispatcher {

    private final EvaluationProperties props;
    private final EvaluationStream stream;
    private final AsyncEvaluator asyncEvaluator;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAttemptSubmitted(AttemptSubmittedEvent event) {
        if (props.mode() == EvaluationProperties.Mode.STREAM) {
            try {
                stream.publish(event.attemptId());
                return;
            } catch (RuntimeException e) {
                log.warn("Could not enqueue evaluation of {} ({}); evaluating in-process", event.attemptId(),
                        e.getMessage());
            }
        }
        asyncEvaluator.evaluate(event.attemptId());
    }

    @Component
    @RequiredArgsConstructor
    static class AsyncEvaluator {

        private final EvaluationService evaluation;

        @Async
        public void evaluate(java.util.UUID attemptId) {
            try {
                evaluation.evaluate(attemptId, false);
            } catch (RuntimeException e) {
                log.warn("Async evaluation of {} failed (the DB sweep will retry): {}", attemptId, e.getMessage());
            }
        }
    }
}
