package com.examprep.payment.dto;

import com.examprep.enrollment.dto.EnrollmentDto;
import com.examprep.payment.entity.PaymentProvider;
import com.examprep.payment.entity.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record CreateOrderRequest(@NotNull UUID seriesId) {
    }

    /**
     * Everything the frontend needs to open Razorpay Checkout
     * ({@code new Razorpay({key, order_id, amount, currency, name, description, prefill})}).
     * With {@code mock = true}, call {@code POST /payments/{paymentId}/mock-checkout} instead of opening Checkout.
     */
    public record CheckoutDto(UUID paymentId, PaymentProvider provider, String keyId, String orderId,
                              long amountMinor, String currency, String name, String description,
                              String prefillName, String prefillEmail, String prefillContact, boolean mock) {
    }

    /** The three values Razorpay Checkout returns in its success handler. */
    public record VerifyPaymentRequest(
            @NotBlank @Size(max = 100) String orderId,
            @NotBlank @Size(max = 100) String paymentId,
            @NotBlank @Size(max = 256) String signature) {
    }

    public record PaymentDto(UUID id, UUID userId, UUID seriesId, BigDecimal amount, String currency,
                             PaymentProvider provider, PaymentStatus status, String receipt, String providerOrderId,
                             String providerPaymentId, String failureReason, Instant paidAt, Instant createdAt) {
    }

    public record VerifyPaymentResponse(PaymentDto payment, EnrollmentDto enrollment) {
    }
}
