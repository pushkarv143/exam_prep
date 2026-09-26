package com.examprep.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;

import java.util.Set;
import java.util.UUID;

/** One row of {@code audit_log}, before the diff is computed. */
@Builder
public record AuditRecord(
        UUID actorId,
        String actorEmail,
        Set<String> actorRoles,
        String action,
        String entityType,
        String entityId,
        Outcome outcome,
        String httpMethod,
        String path,
        Integer statusCode,
        String errorCode,
        String reason,
        JsonNode before,
        JsonNode after,
        JsonNode metadata,
        String ip,
        String userAgent,
        String requestId) {

    public enum Outcome { SUCCESS, FAILURE, DENIED }
}
