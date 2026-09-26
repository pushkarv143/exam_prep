package com.examprep.notification.outbox;

import com.examprep.notification.entity.NotificationChannel;
import com.examprep.notification.entity.NotificationTemplate;
import com.examprep.notification.service.NotificationService;
import com.examprep.outbox.OutboxHandler;
import com.examprep.outbox.OutboxService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Guaranteed notifications: {@link #enqueue} records the intent in the caller's transaction
 * (outbox), and the relay then hands it to {@link NotificationService}, which renders, stores
 * and sends it and retries failed deliveries. Secrets (reset links) are wiped from the
 * outbox row after delivery.
 */
@Component
@RequiredArgsConstructor
public class NotificationOutbox implements OutboxHandler {

    public static final String TYPE = "notification.send";

    private final OutboxService outbox;
    private final NotificationService notifications;
    private final ObjectMapper json;

    public void enqueue(UUID userId, NotificationChannel channel, NotificationTemplate template, String recipient,
                        Map<String, ?> variables) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("channel", channel.name());
        payload.put("template", template.name());
        payload.put("recipient", recipient);
        payload.put("variables", variables);
        outbox.enqueue(TYPE, "USER", userId, payload);
    }

    @Override
    public String eventType() {
        return TYPE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handle(UUID eventId, JsonNode p) {
        Map<String, Object> vars = json.convertValue(p.get("variables"), Map.class);
        notifications.send(p.hasNonNull("userId") ? UUID.fromString(p.get("userId").asText()) : null,
                NotificationChannel.valueOf(p.get("channel").asText()),
                NotificationTemplate.valueOf(p.get("template").asText()),
                p.get("recipient").asText(), vars);
    }

    @Override
    public boolean redactAfterSend(JsonNode payload) {
        try {
            return NotificationTemplate.valueOf(payload.get("template").asText()).isSensitive();
        } catch (RuntimeException e) {
            return true;
        }
    }
}
