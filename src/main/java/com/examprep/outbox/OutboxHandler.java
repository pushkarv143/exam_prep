package com.examprep.outbox;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * Consumes one outbox event type. Delivery is at-least-once: after a crash an event may be
 * handled twice, so handlers must be idempotent, or tolerate a duplicate (e.g. an extra
 * email). The event id is a stable deduplication key. Throwing schedules a retry.
 */
public interface OutboxHandler {

    String eventType();

    void handle(UUID eventId, JsonNode payload) throws Exception;

    /** When true, the stored payload is replaced after delivery (it held a secret). */
    default boolean redactAfterSend(JsonNode payload) {
        return false;
    }
}
