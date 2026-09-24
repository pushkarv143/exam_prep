package com.examprep.payment.gateway;

import com.examprep.payment.entity.PaymentProvider;

import java.util.Map;

/**
 * Payment-provider port. Implementations: {@link RazorpayGateway} and
 * {@link MockPaymentGateway}. Exactly one is active, chosen by {@code app.payment.provider}.
 * To add another provider (e.g. Cashfree), implement this interface and add an enum value.
 */
public interface PaymentGateway {

    PaymentProvider provider();

    /** Public key the browser checkout needs (never the secret). */
    String publicKeyId();

    /**
     * @param amountMinor amount in paise
     * @throws com.examprep.common.exception.BusinessException PAYMENT_GATEWAY_ERROR on provider failure
     */
    GatewayOrder createOrder(long amountMinor, String currency, String receipt, Map<String, String> notes);

    /** Verifies the signature the checkout returns to the browser after a successful payment. */
    boolean verifyPaymentSignature(String orderId, String paymentId, String signature);

    /** Verifies a webhook against its raw, unparsed body. */
    boolean verifyWebhookSignature(String rawBody, String signature);

    record GatewayOrder(String orderId, long amountMinor, String currency) {
    }
}
