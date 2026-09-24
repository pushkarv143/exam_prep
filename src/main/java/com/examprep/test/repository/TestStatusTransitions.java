package com.examprep.test.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Time-driven status changes as single set-based {@code UPDATE ... RETURNING} statements.
 *
 * <ul>
 *   <li><b>Multi-instance safe without a lock.</b> Each row changes in exactly one
 *       statement, so only one instance gets a given id back and publishes its event.</li>
 *   <li>{@code version + 1} keeps JPA optimistic locking honest. An admin editing the
 *       test concurrently gets a 409 instead of silently overwriting the new status.</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class TestStatusTransitions {

    private final EntityManager entityManager;

    /** PUBLISHED to LIVE for tests whose window has opened (and not already closed). */
    @SuppressWarnings("unchecked")
    public List<UUID> activateDue(Instant now) {
        return entityManager.createNativeQuery("""
                        UPDATE tests SET status = 'LIVE', version = version + 1, updated_at = :now
                        WHERE status = 'PUBLISHED' AND start_at IS NOT NULL AND start_at <= :now
                          AND (end_at IS NULL OR end_at > :now)
                        RETURNING id
                        """, UUID.class)
                .setParameter("now", now)
                .getResultList();
    }

    /** PUBLISHED or LIVE to COMPLETED for tests whose window has closed. */
    @SuppressWarnings("unchecked")
    public List<UUID> completeDue(Instant now) {
        return entityManager.createNativeQuery("""
                        UPDATE tests SET status = 'COMPLETED', version = version + 1, updated_at = :now
                        WHERE status IN ('PUBLISHED', 'LIVE') AND end_at IS NOT NULL AND end_at <= :now
                        RETURNING id
                        """, UUID.class)
                .setParameter("now", now)
                .getResultList();
    }
}
