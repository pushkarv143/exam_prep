package com.examprep.attempt.paper;

import com.examprep.test.event.TestPaperChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps cached papers in step with their sources. It runs after commit, so the rebuilt
 * paper reads committed data, and asynchronously, so admin requests do not wait for a
 * paper build.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaperCacheListener {

    private final PaperService papers;

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onTestPaperChanged(TestPaperChangedEvent event) {
        try {
            if (event.warm()) {
                papers.warm(event.testId());
            } else {
                papers.evict(event.testId());
            }
        } catch (RuntimeException e) {
            log.warn("Paper cache refresh failed for test {}: {}", event.testId(), e.getMessage());
        }
    }
}
