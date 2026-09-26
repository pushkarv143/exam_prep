package com.examprep.jobs;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public final class JobDtos {

    private JobDtos() {
    }

    public record JobDto(UUID id, String type, JobStatus status, int progress, String progressMessage,
                         JsonNode params, JsonNode result, String error, int attempts, int maxAttempts,
                         boolean cancelRequested, UUID createdBy, String createdByName, Instant createdAt,
                         Instant startedAt, Instant finishedAt, Instant runAfter, ArtifactDto artifact) {
    }

    public record ArtifactDto(String filename, String contentType, long sizeBytes) {
    }

    public record Artifact(String filename, String contentType, byte[] data) {
    }
}
