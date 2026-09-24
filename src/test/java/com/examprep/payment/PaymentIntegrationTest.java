package com.examprep.payment;

import com.examprep.payment.gateway.MockPaymentGateway;
import com.examprep.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Checkout, verification and webhook flows on the MOCK provider (the same HMAC scheme as Razorpay). */
class PaymentIntegrationTest extends AbstractIntegrationTest {

    static final String PAID_SERIES = "15000000-0000-7000-8000-000000000002";
    static final String PAID_TEST = "16000000-0000-7000-8000-000000000002";

    @Autowired
    MockPaymentGateway mockGateway;

    private JsonNode order(String token) throws Exception {
        return body(mvc.perform(post("/api/v1/payments/orders").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("seriesId", PAID_SERIES))))
                .andExpect(status().isCreated()).andReturn()).at("/data");
    }

    @Test
    void checkout_verify_grants_access_exactly_once() throws Exception {
        String buyer = bearer(registerStudent());
        JsonNode checkout = order(buyer);
        assertThat(checkout.at("/amountMinor").asLong()).isEqualTo(49_900);    // Rs 499 in paise
        assertThat(checkout.at("/mock").asBoolean()).isTrue();
        assertThat(checkout.at("/prefillEmail").asText()).endsWith("@example.com");

        // A double click reuses the same order.
        assertThat(order(buyer).at("/orderId").asText()).isEqualTo(checkout.at("/orderId").asText());

        // A forged signature is rejected, and no access is granted.
        mvc.perform(post("/api/v1/payments/verify").header("Authorization", buyer)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "orderId", checkout.at("/orderId").asText(), "paymentId", "pay_fake",
                                "signature", "deadbeef"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_VERIFICATION_FAILED"));

        MockPaymentGateway.SimulatedCheckout ok = mockGateway.simulateCheckout(checkout.at("/orderId").asText());
        String verifyBody = json.writeValueAsString(Map.of("orderId", ok.orderId(), "paymentId", ok.paymentId(),
                "signature", ok.signature()));
        mvc.perform(post("/api/v1/payments/verify").header("Authorization", buyer)
                        .contentType(MediaType.APPLICATION_JSON).content(verifyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payment.status").value("PAID"))
                .andExpect(jsonPath("$.data.enrollment.source").value("PAYMENT"))
                .andExpect(jsonPath("$.data.enrollment.active").value(true));

        // Replaying verify is idempotent.
        mvc.perform(post("/api/v1/payments/verify").header("Authorization", buyer)
                        .contentType(MediaType.APPLICATION_JSON).content(verifyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payment.status").value("PAID"));

        mvc.perform(get("/api/v1/tests/" + PAID_TEST).header("Authorization", buyer))
                .andExpect(jsonPath("$.data.hasAccess").value(true));

        // Buying again is refused.
        mvc.perform(post("/api/v1/payments/orders").header("Authorization", buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("seriesId", PAID_SERIES))))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/v1/payments/mine").header("Authorization", buyer))
                .andExpect(jsonPath("$.data[0].status").value("PAID"));
    }

    @Test
    void another_users_order_cannot_be_verified() throws Exception {
        String owner = bearer(registerStudent());
        String thief = bearer(registerStudent());
        MockPaymentGateway.SimulatedCheckout ok = mockGateway.simulateCheckout(order(owner).at("/orderId").asText());
        mvc.perform(post("/api/v1/payments/verify").header("Authorization", thief)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "orderId", ok.orderId(), "paymentId", ok.paymentId(), "signature", ok.signature()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void webhook_captures_payment_when_browser_never_returns() throws Exception {
        String buyer = bearer(registerStudent());
        String orderId = order(buyer).at("/orderId").asText();
        String eventId = "evt_" + UUID.randomUUID();
        String payload = webhook("payment.captured", orderId, "pay_wh_" + eventId.substring(4, 14), 49_900);

        mvc.perform(post("/api/v1/payments/webhook/razorpay")
                        .header("X-Razorpay-Signature", mockGateway.signWebhook(payload))
                        .header("X-Razorpay-Event-Id", eventId)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
        // Provider retry of the same event: acknowledged, not reprocessed.
        mvc.perform(post("/api/v1/payments/webhook/razorpay")
                        .header("X-Razorpay-Signature", mockGateway.signWebhook(payload))
                        .header("X-Razorpay-Event-Id", eventId)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/payments/mine").header("Authorization", buyer))
                .andExpect(jsonPath("$.data[0].status").value("PAID"));
        mvc.perform(get("/api/v1/tests/" + PAID_TEST).header("Authorization", buyer))
                .andExpect(jsonPath("$.data.hasAccess").value(true));
    }

    @Test
    void webhook_rejects_bad_signature_and_ignores_wrong_amount() throws Exception {
        String buyer = bearer(registerStudent());
        String orderId = order(buyer).at("/orderId").asText();

        String payload = webhook("payment.captured", orderId, "pay_x", 49_900);
        mvc.perform(post("/api/v1/payments/webhook/razorpay").header("X-Razorpay-Signature", "bad")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());

        String underpaid = webhook("payment.captured", orderId, "pay_y", 100);
        mvc.perform(post("/api/v1/payments/webhook/razorpay")
                        .header("X-Razorpay-Signature", mockGateway.signWebhook(underpaid))
                        .header("X-Razorpay-Event-Id", "evt_" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(underpaid))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/payments/mine").header("Authorization", buyer))
                .andExpect(jsonPath("$.data[0].status").value("CREATED"))
                .andExpect(jsonPath("$.data[0].failureReason").value("Amount mismatch: captured 100"));
        mvc.perform(get("/api/v1/tests/" + PAID_TEST).header("Authorization", buyer))
                .andExpect(jsonPath("$.data.hasAccess").value(false));
    }

    @Test
    void mock_checkout_endpoint_completes_payment_for_local_frontend_dev() throws Exception {
        String buyer = bearer(registerStudent());
        String paymentId = order(buyer).at("/paymentId").asText();
        mvc.perform(post("/api/v1/payments/" + paymentId + "/mock-checkout").header("Authorization", buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payment.status").value("PAID"));
    }

    @Test
    void free_series_cannot_be_bought() throws Exception {
        mvc.perform(post("/api/v1/payments/orders").header("Authorization", bearer(registerStudent()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("seriesId", "15000000-0000-7000-8000-000000000001"))))
                .andExpect(status().isBadRequest());
    }

    private static String webhook(String event, String orderId, String paymentId, long amount) {
        return """
                {"entity":"event","event":"%s","payload":{"payment":{"entity":{"id":"%s","order_id":"%s","amount":%d,"currency":"INR","status":"captured"}}}}"""
                .formatted(event, paymentId, orderId, amount);
    }
}
