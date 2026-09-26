package com.examprep.outbox;

import com.examprep.common.util.Uuids;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Transactional outbox: {@link #enqueue} inserts the event in the caller's transaction, so
 * it is stored if and only if the business change commits. {@link OutboxRelay} delivers it
 * afterwards with retries. That removes the "committed but the message was lost" window of
 * after-commit listeners.
 */
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    @Transactional(propagation = Propagation.REQUIRED)
    public UUID enqueue(String eventType, String aggregateType, Object aggregateId, Object payload) {
        UUID id = Uuids.v7();
        try {
            jdbc.update("""
                    insert into outbox_events (id, event_type, aggregate_type, aggregate_id, payload)
                    values (?, ?, ?, ?, ?::jsonb)
                    """, id, eventType, aggregateType, aggregateId == null ? null : aggregateId.toString(),
                    json.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Outbox payload is not serialisable", e);
        }
        return id;
    }
}
