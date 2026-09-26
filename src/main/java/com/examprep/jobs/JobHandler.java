package com.examprep.jobs;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Implement as a Spring bean to add a job type. {@link #run} executes on a worker thread,
 * outside any transaction. Report progress through the context, check
 * {@link JobContext#checkCancelled()} in loops, and return a small JSON result (or null).
 * Throwing makes the job retry with backoff, and after {@code maxAttempts} it goes DEAD.
 * Throw {@link JobContext.NonRetryableException} to fail at once.
 */
public interface JobHandler {

    String type();

    JsonNode run(JobContext context) throws Exception;

    /** Default maximum attempts for this job type. */
    default int maxAttempts() {
        return 3;
    }
}
