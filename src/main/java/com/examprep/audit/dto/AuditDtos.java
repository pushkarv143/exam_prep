package com.examprep.audit.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AuditDtos {

    private AuditDtos() {
    }

    /**
     * Search filters. {@code from}/{@code to} bound the time range (default: last 30 days) so the
     * query touches only the relevant monthly partitions.
     */
    public record AuditFilter(Instant from, Instant to, UUID actorId, String actorEmail, String action,
                              String entityType, String entityId, String outcome, String q) {
    }

    public record AuditEntryDto(UUID id, Instant occurredAt, UUID actorId, String actorEmail, String actorRoles,
                                String action, String entityType, String entityId, String outcome,
                                String httpMethod, String path, Integer statusCode, String errorCode,
                                String reason, JsonNode before, JsonNode after, JsonNode changes,
                                JsonNode metadata, String ip, String userAgent, String requestId) {
    }

    /** Keyset page: pass {@code nextCursor} back as {@code cursor} to get the next page. */
    public record AuditPage(List<AuditEntryDto> items, String nextCursor) {
    }
}
