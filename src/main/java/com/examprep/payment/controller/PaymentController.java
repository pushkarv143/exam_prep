package com.examprep.payment.controller;

import com.examprep.common.api.ApiResponse;
import com.examprep.common.ratelimit.RateLimit;
import com.examprep.payment.dto.PaymentDtos.CheckoutDto;
import com.examprep.payment.dto.PaymentDtos.CreateOrderRequest;
import com.examprep.payment.dto.PaymentDtos.PaymentDto;
import com.examprep.payment.dto.PaymentDtos.VerifyPaymentRequest;
import com.examprep.payment.dto.PaymentDtos.VerifyPaymentResponse;
import com.examprep.payment.service.PaymentService;
import com.examprep.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Payments", description = "Razorpay checkout (or mock in dev)")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Start checkout for a paid series; returns the Razorpay Checkout options")
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimit(name = "payment-order", limit = 10, windowSeconds = 60, key = RateLimit.KeyType.USER)
    public ApiResponse<CheckoutDto> createOrder(@AuthenticationPrincipal AuthUser user,
                                                @Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.ok(paymentService.createOrder(user, request.seriesId()));
    }

    @Operation(summary = "Verify the Checkout result and grant access (idempotent)")
    @PostMapping("/verify")
    @RateLimit(name = "payment-verify", limit = 20, windowSeconds = 60, key = RateLimit.KeyType.USER)
    public ApiResponse<VerifyPaymentResponse> verify(@AuthenticationPrincipal AuthUser user,
                                                     @Valid @RequestBody VerifyPaymentRequest request) {
        return ApiResponse.ok(paymentService.verify(user, request));
    }

    @Operation(summary = "DEV ONLY (mock provider): simulate a successful checkout")
    @PostMapping("/{paymentId}/mock-checkout")
    public ApiResponse<VerifyPaymentResponse> mockCheckout(@AuthenticationPrincipal AuthUser user,
                                                           @PathVariable UUID paymentId) {
        return ApiResponse.ok(paymentService.mockCheckout(user, paymentId));
    }

    @Operation(summary = "My payment history")
    @GetMapping("/mine")
    public ApiResponse<List<PaymentDto>> mine(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(paymentService.mine(user.id()));
    }
}
