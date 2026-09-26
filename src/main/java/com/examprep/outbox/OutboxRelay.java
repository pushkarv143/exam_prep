package com.examprep.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Delivers outbox events. Rows are claimed with a 60 s lease through SKIP LOCKED, so every
 * instance can run the relay and none delivers the same event concurrently. Failed deliveries
 * back off exponentially (5 s ... about 21 min). After {@value #MAX_ATTEMPTS} attempts an
 * event is DEAD: kept for inspection and manual replay, never silently dropped.
 */
@Slf4j
@Component
public class OutboxRelay {

    static final int MAX_ATTEMPTS = 9;
    private static final int BATCH = 50;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Map<String, OutboxHandler> handlers;
    private final boolean enabled;

    public OutboxRelay(JdbcTemplate jdbc, ObjectMapper json, List<OutboxHandler> handlerBeans,
                       @Value("${app.outbox.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.json = json;
        this.handlers = handlerBeans.stream().collect(Collectors.toMap(OutboxHandler::eventType, Function.identity()));
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:PT1S}", initialDelayString = "PT5S")
    public void relay() {
        if (!enabled) {
            return;
        }
        List<Claimed> batch = jdbc.query("""
                update outbox_events set attempts = attempts + 1, locked_until = now() + interval '60 seconds'
                where id in (
                    select id from outbox_events
                    where status = 'PENDING' and next_attempt_at <= now()
                      and (locked_until is null or locked_until < now())
                    order by created_at
                    limit ?
                    for update skip locked)
                returning id, event_type, payload, attempts
                """, (rs, i) -> new Claimed(rs.getObject("id", UUID.class), rs.getString("event_type"),
                read(rs.getString("payload")), rs.getInt("attempts")), BATCH);
        for (Claimed e : batch) {
            deliver(e);
        }
    }

    private void deliver(Claimed e) {
        OutboxHandler handler = handlers.get(e.type);
        try {
            if (handler == null) {
                throw new IllegalStateException("No outbox handler for " + e.type);
            }
            handler.handle(e.id, e.payload);
            boolean redact = handler.redactAfterSend(e.payload);
            jdbc.update("update outbox_events set status = 'SENT', sent_at = now(), locked_until = null, last_error = null"
                    + (redact ? ", payload = '{\"redacted\": true}'::jsonb" : "") + " where id = ?", e.id);
        } catch (Exception ex) {
            String error = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            if (e.attempts >= MAX_ATTEMPTS) {
                jdbc.update("update outbox_events set status = 'DEAD', last_error = ?, locked_until = null where id = ?",
                        cut(error), e.id);
                log.error("Outbox event {} ({}) is DEAD after {} attempts: {}", e.id, e.type, e.attempts, error);
            } else {
                long delay = 5L * (1L << Math.min(e.attempts - 1, 8));
                jdbc.update("""
                        update outbox_events set last_error = ?, locked_until = null,
                               next_attempt_at = now() + (? * interval '1 second')
                        where id = ?
                        """, cut(error), delay, e.id);
                log.warn("Outbox event {} ({}) failed attempt {}; retry in {}s: {}", e.id, e.type, e.attempts, delay,
                        error);
            }
        }
    }

    private JsonNode read(String s) {
        try {
            return json.readTree(s);
        } catch (IOException e) {
            return json.getNodeFactory().nullNode();
        }
    }

    private static String cut(String s) {
        return s.length() > 2000 ? s.substring(0, 2000) : s;
    }

    private record Claimed(UUID id, String type, JsonNode payload, int attempts) {
    }
}
