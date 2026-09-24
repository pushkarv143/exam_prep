package com.examprep.payment.gateway;

import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.payment.PaymentProperties;
import com.examprep.payment.entity.PaymentProvider;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Razorpay over its REST API (no SDK). Flow:
 * <ol>
 *   <li>Server: {@code POST /v1/orders} gives an order_id (this class).</li>
 *   <li>Browser: Razorpay Checkout with key_id + order_id. It returns
 *       {@code razorpay_payment_id} and {@code razorpay_signature}.</li>
 *   <li>Server: verify {@code HMAC_SHA256(order_id + "|" + payment_id, key_secret)}.</li>
 *   <li>Razorpay also sends a webhook signed with the webhook secret (the source of truth
 *       if the browser closes before step 3).</li>
 * </ol>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.payment", name = "provider", havingValue = "RAZORPAY")
public class RazorpayGateway implements PaymentGateway {

    private final PaymentProperties.Razorpay config;
    private final RestClient client;

    public RazorpayGateway(PaymentProperties properties, RestClient.Builder builder) {
        this.config = properties.razorpay();
        if (!StringUtils.hasText(config.keyId()) || !StringUtils.hasText(config.keySecret())
                || !StringUtils.hasText(config.webhookSecret())) {
            throw new IllegalStateException("app.payment.razorpay.key-id, key-secret and webhook-secret are "
                    + "required when app.payment.provider=RAZORPAY");
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(config.connectTimeout()).build());
        factory.setReadTimeout(config.readTimeout());
        String basic = Base64.getEncoder().encodeToString(
                (config.keyId() + ":" + config.keySecret()).getBytes(StandardCharsets.UTF_8));
        this.client = builder.baseUrl(config.baseUrl())
                .requestFactory(factory)
                .defaultHeader("Authorization", "Basic " + basic)
                .build();
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.RAZORPAY;
    }

    @Override
    public String publicKeyId() {
        return config.keyId();
    }

    @Override
    public GatewayOrder createOrder(long amountMinor, String currency, String receipt, Map<String, String> notes) {
        try {
            JsonNode res = client.post().uri("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", amountMinor, "currency", currency, "receipt", receipt, "notes", notes))
                    .retrieve()
                    .body(JsonNode.class);
            if (res == null || !res.hasNonNull("id")) {
                throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR);
            }
            return new GatewayOrder(res.get("id").asText(), res.path("amount").asLong(amountMinor),
                    res.path("currency").asText(currency));
        } catch (RestClientException e) {
            log.error("Razorpay order creation failed for receipt {}: {}", receipt, e.getMessage());
            throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR);
        }
    }

    @Override
    public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
        return HmacSha256.matches(config.keySecret(), orderId + "|" + paymentId, signature);
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        return HmacSha256.matches(config.webhookSecret(), rawBody, signature);
    }
}
