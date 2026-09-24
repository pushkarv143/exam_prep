package com.examprep.payment.gateway;

import com.examprep.payment.PaymentProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSha256Test {

    /** Reference value computed independently with: printf 'order|pay' | openssl dgst -sha256 -hmac secret */
    static final String SECRET = "EnLs21M47BllR3X8PSFtjtbd";
    static final String DATA = "order_IluGWxBm9U8zJ8|pay_IH4NVgf4Dreq1l";
    static final String EXPECTED = "c7a6b6e37e5fb8b5387cd93cff2f9cedbf5cb78112fd50292c56b4cecb49b80d";

    @Test
    void matches_openssl_reference() {
        assertThat(HmacSha256.hex(SECRET, DATA)).isEqualTo(EXPECTED);
        assertThat(HmacSha256.matches(SECRET, DATA, EXPECTED.toUpperCase())).isTrue();
    }

    @Test
    void rejects_tampering_and_missing_values() {
        assertThat(HmacSha256.matches(SECRET, DATA + "x", EXPECTED)).isFalse();
        assertThat(HmacSha256.matches("other", DATA, EXPECTED)).isFalse();
        assertThat(HmacSha256.matches(SECRET, DATA, null)).isFalse();
        assertThat(HmacSha256.matches("", DATA, EXPECTED)).isFalse();
    }

    @Test
    void mock_gateway_round_trip_uses_razorpay_scheme() {
        MockPaymentGateway mock = new MockPaymentGateway(new PaymentProperties(null, "x", null, null,
                new PaymentProperties.Mock("s3cret")));
        MockPaymentGateway.SimulatedCheckout c = mock.simulateCheckout("order_1");

        assertThat(mock.verifyPaymentSignature("order_1", c.paymentId(), c.signature())).isTrue();
        assertThat(mock.verifyPaymentSignature("order_2", c.paymentId(), c.signature())).isFalse();
        assertThat(c.signature()).isEqualTo(HmacSha256.hex("s3cret", "order_1|" + c.paymentId()));

        String body = "{\"event\":\"payment.captured\"}";
        assertThat(mock.verifyWebhookSignature(body, mock.signWebhook(body))).isTrue();
        assertThat(mock.verifyWebhookSignature(body + " ", mock.signWebhook(body))).isFalse();
    }
}
