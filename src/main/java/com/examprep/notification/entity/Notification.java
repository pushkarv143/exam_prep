package com.examprep.notification.entity;

import com.examprep.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Durable log of every outbound message: an audit trail, plus the source for retries. */
@Getter
@Setter
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationTemplate template;

    @Column(nullable = false)
    private String recipient;

    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "sent_at")
    private Instant sentAt;
}
