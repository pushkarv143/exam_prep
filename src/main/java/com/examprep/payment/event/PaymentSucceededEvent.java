package com.examprep.payment.event;

import java.math.BigDecimal;
import java.util.UUID;

/** Published exactly once per successful payment, after the enrollment is granted. */
public record PaymentSucceededEvent(UUID paymentId, UUID userId, UUID seriesId, String seriesName,
                                    BigDecimal amount, String currency, String providerPaymentId) {
}
