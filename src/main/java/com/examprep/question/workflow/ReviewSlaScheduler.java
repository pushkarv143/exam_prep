package com.examprep.question.workflow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Reminds reviewers (or, for unassigned reviews, all eligible reviewers) once a review passes its SLA. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewSlaScheduler {

    private final QuestionWorkflowService workflow;

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT3M")
    public void remind() {
        try {
            int n = workflow.sendOverdueReminders();
            if (n > 0) {
                log.info("Sent overdue reminders for {} question review(s)", n);
            }
        } catch (RuntimeException e) {
            log.warn("Review SLA check failed: {}", e.getMessage());
        }
    }
}
