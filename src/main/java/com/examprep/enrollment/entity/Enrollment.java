package com.examprep.enrollment.entity;

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

/** A student's access to a test series. One row per (user, series); renewals update it in place. */
@Getter
@Setter
@Entity
@Table(name = "enrollments")
public class Enrollment extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "series_id", nullable = false, updatable = false)
    private UUID seriesId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnrollmentSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnrollmentStatus status = EnrollmentStatus.ACTIVE;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    /** Null = never expires. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    public boolean isActiveAt(Instant now) {
        return status == EnrollmentStatus.ACTIVE && (expiresAt == null || expiresAt.isAfter(now));
    }
}
