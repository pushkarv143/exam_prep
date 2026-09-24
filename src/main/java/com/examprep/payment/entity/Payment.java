package com.examprep.payment.entity;

import com.examprep.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One checkout attempt for one series. The amount is copied from the series price at order time. */
@Getter
@Setter
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "series_id", nullable = false, updatable = false)
    private UUID seriesId;

    @Column(nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.CREATED;

    /** Our idempotency/reference key, sent to the provider as {@code receipt}. */
    @Column(nullable = false, unique = true, updatable = false)
    private String receipt;

    @Column(name = "provider_order_id", unique = true)
    private String providerOrderId;

    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    @Column(name = "provider_signature")
    private String providerSignature;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "paid_at")
    private Instant paidAt;

    /** Amount in the smallest currency unit (paise), as payment providers expect. */
    public long amountMinor() {
        return amount.setScale(2, java.math.RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    }
}
