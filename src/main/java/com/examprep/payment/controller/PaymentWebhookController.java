package com.examprep.payment.controller;

import com.examprep.common.api.ApiResponse;
import com.examprep.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Razorpay webhook receiver. It is public, but authenticated by the HMAC signature over
 * the <b>raw</b> body. The body is bound as a String, so the exact bytes Razorpay signed
 * are verified (re-serialising parsed JSON would change them).
 *
 * <p>Configure it in the Razorpay dashboard: URL {@code https://<host>/api/v1/payments/webhook/razorpay},
 * events payment.captured, payment.failed, order.paid, and the secret = {@code RAZORPAY_WEBHOOK_SECRET}.
 */
@Tag(name = "Payments")
@SecurityRequirements
@RestController
@RequestMapping("/api/v1/payments/webhook")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentService paymentService;

    @Operation(summary = "Razorpay webhook (signature-verified)")
    @PostMapping("/razorpay")
    public ApiResponse<Void> razorpay(@RequestBody String rawBody,
                                      @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature,
                                      @RequestHeader(name = "X-Razorpay-Event-Id", required = false) String eventId) {
        paymentService.handleWebhook(rawBody, signature, eventId);
        return ApiResponse.ok();
    }
}
