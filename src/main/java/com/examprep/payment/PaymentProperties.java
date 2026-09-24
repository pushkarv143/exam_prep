package com.examprep.payment;

import com.examprep.payment.entity.PaymentProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * {@code app.payment.*}
 *
 * @param provider         MOCK (dev/test, no network) or RAZORPAY
 * @param orderReuseWindow a CREATED order for the same user and series younger than
 *                         this is reused, so double clicks do not create multiple orders
 */
@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
        @DefaultValue("MOCK") PaymentProvider provider,
        @DefaultValue("ExamPrep") String brandName,
        @DefaultValue("30m") Duration orderReuseWindow,
        @DefaultValue Razorpay razorpay,
        @DefaultValue Mock mock) {

    public record Razorpay(
            String keyId,
            String keySecret,
            String webhookSecret,
            @DefaultValue("https://api.razorpay.com/v1") String baseUrl,
            @DefaultValue("5s") Duration connectTimeout,
            @DefaultValue("15s") Duration readTimeout) {
    }

    /** HMAC secret the mock gateway uses for its payment and webhook signatures. */
    public record Mock(@DefaultValue("mock-dev-secret") String secret) {
    }
}
