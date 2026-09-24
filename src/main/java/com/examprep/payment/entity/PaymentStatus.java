package com.examprep.payment.entity;

/**
 * <pre>
 * CREATED ──(signature verified / webhook captured)──▶ PAID ──(admin refund)──▶ REFUNDED
 *    └──(payment.failed webhook / gateway error)──▶ FAILED ──(retry on same order succeeds)──▶ PAID
 * </pre>
 */
public enum PaymentStatus {
    CREATED,
    PAID,
    FAILED,
    REFUNDED
}
