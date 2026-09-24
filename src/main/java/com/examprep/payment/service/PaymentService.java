package com.examprep.payment.service;

import com.examprep.common.api.PageResponse;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.exception.NotFoundException;
import com.examprep.common.util.Hashing;
import com.examprep.enrollment.dto.EnrollmentDto;
import com.examprep.payment.PaymentProperties;
import com.examprep.payment.dto.PaymentDtos.CheckoutDto;
import com.examprep.payment.dto.PaymentDtos.PaymentDto;
import com.examprep.payment.dto.PaymentDtos.VerifyPaymentRequest;
import com.examprep.payment.dto.PaymentDtos.VerifyPaymentResponse;
import com.examprep.payment.entity.Payment;
import com.examprep.payment.entity.PaymentProvider;
import com.examprep.payment.entity.PaymentStatus;
import com.examprep.payment.event.PaymentSucceededEvent;
import com.examprep.payment.gateway.MockPaymentGateway;
import com.examprep.payment.gateway.PaymentGateway;
import com.examprep.payment.gateway.PaymentGateway.GatewayOrder;
import com.examprep.payment.repository.PaymentRepository;
import com.examprep.security.AuthUser;
import com.examprep.test.entity.TestSeries;
import com.examprep.test.repository.TestSeriesRepository;
import com.examprep.test.service.SeriesAccessService;
import com.examprep.user.dto.UserDto;
import com.examprep.user.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Checkout, verification and webhooks.
 *
 * <p>Guarantees:
 * <ul>
 *   <li><b>No DB connection is held during provider HTTP calls.</b> The order is saved,
 *       the provider is called outside any transaction, then the provider order id is
 *       saved.</li>
 *   <li><b>Exactly-once fulfilment.</b> Verify and webhook both lock the payment row
 *       ({@code FOR UPDATE}). Whichever comes second sees PAID and returns the same
 *       result without granting access twice.</li>
 *   <li><b>Never trust the client.</b> Access is granted only after a valid HMAC
 *       signature. The webhook additionally cross-checks the captured amount.</li>
 *   <li><b>Webhook idempotency.</b> Each provider event id is processed once, in the
 *       same transaction as its effects.</li>
 * </ul>
 */
@Slf4j
@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final TestSeriesRepository seriesRepository;
    private final SeriesAccessService access;
    private final UserService userService;
    private final PaymentGateway gateway;
    private final PaymentProperties properties;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, TestSeriesRepository seriesRepository,
                          SeriesAccessService access, UserService userService, PaymentGateway gateway,
                          PaymentProperties properties, ApplicationEventPublisher events, ObjectMapper objectMapper,
                          PlatformTransactionManager transactionManager, Clock clock) {
        this.payments = payments;
        this.seriesRepository = seriesRepository;
        this.access = access;
        this.userService = userService;
        this.gateway = gateway;
        this.properties = properties;
        this.events = events;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    // ------------------------------------------------------------------ checkout

    public CheckoutDto createOrder(AuthUser user, UUID seriesId) {
        TestSeries series = access.requirePurchasable(user, seriesId);
        UserDto buyer = userService.get(user.id());

        // Reuse a recent open order: a double click or page refresh must not create a second order.
        Instant reuseAfter = Instant.now(clock).minus(properties.orderReuseWindow());
        Payment existing = payments.findFirstByUserIdAndSeriesIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                        user.id(), seriesId, PaymentStatus.CREATED, reuseAfter)
                .filter(p -> p.getProviderOrderId() != null && p.getAmount().compareTo(series.getPrice()) == 0
                        && p.getProvider() == gateway.provider())
                .orElse(null);
        if (existing != null) {
            return checkout(existing, series, buyer);
        }

        // 1) Persist our intent (short transaction).
        Payment payment = tx.execute(status -> {
            Payment p = new Payment();
            p.setUserId(user.id());
            p.setSeriesId(seriesId);
            p.setAmount(series.getPrice());
            p.setCurrency(series.getCurrency());
            p.setProvider(gateway.provider());
            p.setReceipt("rcpt_" + Hashing.randomUrlToken(15));
            return payments.save(p);
        });

        // 2) Talk to the provider with no DB transaction or connection held.
        GatewayOrder order;
        try {
            order = gateway.createOrder(payment.amountMinor(), payment.getCurrency(), payment.getReceipt(),
                    Map.of("paymentId", payment.getId().toString(), "seriesId", seriesId.toString(),
                            "userId", user.id().toString()));
        } catch (BusinessException e) {
            tx.executeWithoutResult(status -> payments.findById(payment.getId()).ifPresent(p -> {
                p.setStatus(PaymentStatus.FAILED);
                p.setFailureReason("Order creation failed at provider");
            }));
            throw e;
        }

        // 3) Record the provider order id.
        Payment saved = tx.execute(status -> {
            Payment p = payments.findById(payment.getId()).orElseThrow();
            p.setProviderOrderId(order.orderId());
            return p;
        });
        log.info("Payment {} created: order {} for series {} ({} {})", saved.getId(), order.orderId(), seriesId,
                saved.getAmount(), saved.getCurrency());
        return checkout(saved, series, buyer);
    }

    /** Called by the frontend with the values from the Checkout success handler. */
    @Transactional
    public VerifyPaymentResponse verify(AuthUser user, VerifyPaymentRequest req) {
        if (!gateway.verifyPaymentSignature(req.orderId(), req.paymentId(), req.signature())) {
            log.warn("Invalid payment signature from user {} for order {}", user.id(), req.orderId());
            throw new BusinessException(ErrorCode.PAYMENT_VERIFICATION_FAILED);
        }
        Payment payment = payments.lockByProviderOrderId(req.orderId())
                .filter(p -> p.getUserId().equals(user.id()))
                .orElseThrow(() -> new NotFoundException("Order not found: " + req.orderId()));
        EnrollmentDto enrollment = markPaid(payment, req.paymentId(), req.signature());
        return new VerifyPaymentResponse(toDto(payment), enrollment);
    }

    /** Dev/test only (mock provider): simulates a successful checkout and verifies it. */
    @Transactional
    public VerifyPaymentResponse mockCheckout(AuthUser user, UUID paymentId) {
        if (!(gateway instanceof MockPaymentGateway mock)) {
            throw new NotFoundException("Mock checkout is disabled");
        }
        Payment payment = payments.findById(paymentId).filter(p -> p.getUserId().equals(user.id()))
                .orElseThrow(() -> NotFoundException.of("Payment", paymentId));
        MockPaymentGateway.SimulatedCheckout result = mock.simulateCheckout(payment.getProviderOrderId());
        return verify(user, new VerifyPaymentRequest(result.orderId(), result.paymentId(), result.signature()));
    }

    // ------------------------------------------------------------------ webhook

    /**
     * Handles a provider webhook. Invalid signatures are rejected (400). Unknown orders and
     * events are acknowledged and ignored (200), so the provider does not retry forever.
     */
    @Transactional
    public void handleWebhook(String rawBody, String signature, String eventIdHeader) {
        if (!gateway.verifyWebhookSignature(rawBody, signature)) {
            throw new BusinessException(ErrorCode.PAYMENT_VERIFICATION_FAILED, "Invalid webhook signature");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Malformed webhook body");
        }
        String eventType = root.path("event").asText("");
        String eventId = eventIdHeader != null && !eventIdHeader.isBlank() ? eventIdHeader : Hashing.sha256Hex(rawBody);
        if (payments.recordEvent(gateway.provider().name(), eventId, eventType, rawBody) == 0) {
            log.info("Duplicate webhook {} ({}) ignored", eventId, eventType);
            return;
        }

        JsonNode entity = root.path("payload").path("payment").path("entity");
        String orderId = entity.path("order_id").asText(null);
        if (orderId == null) {
            log.info("Webhook {} has no payment entity; ignored", eventType);
            return;
        }
        Payment payment = payments.lockByProviderOrderId(orderId).orElse(null);
        if (payment == null) {
            log.warn("Webhook {} for unknown order {}; ignored", eventType, orderId);
            return;
        }

        switch (eventType) {
            case "payment.captured", "order.paid" -> {
                long captured = entity.path("amount").asLong(-1);
                if (captured != payment.amountMinor()) {
                    log.error("Amount mismatch on order {}: expected {}, captured {}", orderId,
                            payment.amountMinor(), captured);
                    payment.setFailureReason("Amount mismatch: captured " + captured);
                    return;
                }
                markPaid(payment, entity.path("id").asText(), null);
            }
            case "payment.failed" -> {
                if (payment.getStatus() == PaymentStatus.CREATED || payment.getStatus() == PaymentStatus.FAILED) {
                    payment.setStatus(PaymentStatus.FAILED);
                    payment.setFailureReason(truncate(entity.path("error_description").asText("Payment failed")));
                }
            }
            default -> log.debug("Webhook event {} not handled", eventType);
        }
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<PaymentDto> mine(UUID userId) {
        return payments.findByUserIdOrderByCreatedAtDesc(userId).stream().map(PaymentService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentDto> search(PaymentStatus status, UUID userId, UUID seriesId, Pageable pageable) {
        return PageResponse.of(payments.search(status, userId, seriesId, pageable), PaymentService::toDto);
    }

    // ------------------------------------------------------------------ internals

    /** Caller must hold the row lock. Idempotent: an already-PAID payment returns its enrollment again. */
    private EnrollmentDto markPaid(Payment payment, String providerPaymentId, String signature) {
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new BusinessException(ErrorCode.CONFLICT, "Payment was refunded");
        }
        if (payment.getStatus() == PaymentStatus.PAID) {
            if (!payment.getProviderPaymentId().equals(providerPaymentId)) {
                // Same order paid twice (rare, e.g. two tabs). Needs a manual refund of the second one.
                log.error("Order {} already paid by {}; second payment {} needs a refund",
                        payment.getProviderOrderId(), payment.getProviderPaymentId(), providerPaymentId);
            }
            return access.grantPaid(payment.getUserId(), payment.getSeriesId(), payment.getId());
        }
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(Instant.now(clock));
        payment.setProviderPaymentId(providerPaymentId);
        if (signature != null) {
            payment.setProviderSignature(signature);
        }
        payment.setFailureReason(null);

        EnrollmentDto enrollment = access.grantPaid(payment.getUserId(), payment.getSeriesId(), payment.getId());
        String seriesName = seriesRepository.findById(payment.getSeriesId()).map(TestSeries::getName).orElse("");
        events.publishEvent(new PaymentSucceededEvent(payment.getId(), payment.getUserId(), payment.getSeriesId(),
                seriesName, payment.getAmount(), payment.getCurrency(), providerPaymentId));
        log.info("Payment {} PAID ({}); enrollment granted", payment.getId(), providerPaymentId);
        return enrollment;
    }

    private CheckoutDto checkout(Payment p, TestSeries series, UserDto buyer) {
        return new CheckoutDto(p.getId(), p.getProvider(), gateway.publicKeyId(), p.getProviderOrderId(),
                p.amountMinor(), p.getCurrency(), properties.brandName(), series.getName(), buyer.fullName(),
                buyer.email(), buyer.phone(), p.getProvider() == PaymentProvider.MOCK);
    }

    private static String truncate(String s) {
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    static PaymentDto toDto(Payment p) {
        return new PaymentDto(p.getId(), p.getUserId(), p.getSeriesId(), p.getAmount(), p.getCurrency(),
                p.getProvider(), p.getStatus(), p.getReceipt(), p.getProviderOrderId(), p.getProviderPaymentId(),
                p.getFailureReason(), p.getPaidAt(), p.getCreatedAt());
    }
}
