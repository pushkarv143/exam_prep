package com.examprep.result.service;

import com.examprep.test.event.TestPaperChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Drops the cached scoring spec when a test's structure or question versions change. */
@Component
@RequiredArgsConstructor
public class EvaluationSpecEvictionListener {

    private final EvaluationSpecCache specs;

    @TransactionalEventListener(fallbackExecution = true)
    public void onTestPaperChanged(TestPaperChangedEvent event) {
        specs.evict(event.testId());
    }
}
