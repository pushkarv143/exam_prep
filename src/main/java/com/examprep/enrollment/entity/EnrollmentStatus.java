package com.examprep.enrollment.entity;

public enum EnrollmentStatus {
    ACTIVE,
    /** Validity ran out. Access checks also compare {@code expires_at} directly, so this flag is informational. */
    EXPIRED,
    /** Revoked by an admin (refund, abuse). */
    CANCELLED
}
