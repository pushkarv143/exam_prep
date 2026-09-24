package com.examprep.payment.gateway;

import com.examprep.common.util.Hashing;
import com.examprep.payment.PaymentProperties;
import com.examprep.payment.entity.PaymentProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Network-free stand-in for Razorpay (dev/test, the default). It uses the same HMAC
 * scheme with a local secret, so the whole verify and webhook code path runs exactly
 * as in production. {@link #simulateCheckout} plays the part of the browser checkout.
 */
@Component
@ConditionalOnProperty(prefix = "app.payment", name = "provider", havingValue = "MOCK", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    private final String secret;

    public MockPaymentGateway(PaymentProperties properties) {
        this.secret = properties.mock().secret();
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.MOCK;
    }

    @Override
    public String publicKeyId() {
        return "rzp_test_mock";
    }

    @Override
    public GatewayOrder createOrder(long amountMinor, String currency, String receipt, Map<String, String> notes) {
        return new GatewayOrder("order_mock_" + Hashing.randomUrlToken(10), amountMinor, currency);
    }

    @Override
    public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
        return HmacSha256.matches(secret, orderId + "|" + paymentId, signature);
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        return HmacSha256.matches(secret, rawBody, signature);
    }

    /** What Razorpay Checkout would hand the browser after a successful payment. */
    public SimulatedCheckout simulateCheckout(String orderId) {
        String paymentId = "pay_mock_" + Hashing.randomUrlToken(10);
        return new SimulatedCheckout(orderId, paymentId, HmacSha256.hex(secret, orderId + "|" + paymentId));
    }

    /** Signs a webhook body the way Razorpay would (for tests and local tooling). */
    public String signWebhook(String rawBody) {
        return HmacSha256.hex(secret, rawBody);
    }

    public record SimulatedCheckout(String orderId, String paymentId, String signature) {
    }
}
