package com.examprep.jobs;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/** What a running job can see and do. */
public interface JobContext {

    UUID jobId();

    JsonNode params();

    UUID createdBy();

    /** 0..100 plus a short message. Throttled, so call it as often as you like. */
    void progress(int percent, String message);

    /** Throws {@link CancelledException} when cancellation was requested. Cheap: the flag is cached briefly. */
    void checkCancelled();

    /** Stores the job's downloadable output (one per job; a later call replaces it). */
    void saveArtifact(String filename, String contentType, byte[] data);

    class CancelledException extends RuntimeException {
        public CancelledException() {
            super("Job cancelled");
        }
    }

    /** Fails the job immediately, without retries (e.g. invalid parameters). */
    class NonRetryableException extends RuntimeException {
        public NonRetryableException(String message) {
            super(message);
        }
    }
}
