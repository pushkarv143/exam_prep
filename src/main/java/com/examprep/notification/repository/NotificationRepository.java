package com.examprep.notification.repository;

import com.examprep.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * Claims a batch of retryable notifications. {@code FOR UPDATE SKIP LOCKED} lets
     * several app instances run the retry job at the same time without sending the
     * same message twice.
     */
    @Query(value = """
            SELECT * FROM notifications
            WHERE status = 'FAILED' AND attempt_count < :maxAttempts AND template NOT IN (:excludedTemplates)
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Notification> lockRetryBatch(@Param("maxAttempts") int maxAttempts,
                                      @Param("excludedTemplates") Collection<String> excludedTemplates,
                                      @Param("batchSize") int batchSize);
}
