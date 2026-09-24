package com.examprep.enrollment.dto;

import com.examprep.enrollment.entity.EnrollmentSource;
import com.examprep.enrollment.entity.EnrollmentStatus;

import java.time.Instant;
import java.util.UUID;

/** @param active status is ACTIVE and not yet expired (evaluated at read time) */
public record EnrollmentDto(UUID id, UUID userId, UUID seriesId, EnrollmentSource source, EnrollmentStatus status,
                            Instant enrolledAt, Instant expiresAt, UUID paymentId, boolean active) {
}
