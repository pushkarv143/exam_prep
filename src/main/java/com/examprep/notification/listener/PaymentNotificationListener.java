package com.examprep.notification.listener;

import com.examprep.common.config.AppProperties;
import com.examprep.notification.entity.NotificationChannel;
import com.examprep.notification.entity.NotificationTemplate;
import com.examprep.notification.outbox.NotificationOutbox;
import com.examprep.payment.event.PaymentSucceededEvent;
import com.examprep.user.dto.UserDto;
import com.examprep.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/** Queues the payment receipt email in the payment transaction (outbox), so it is sent exactly when the payment commits. */
@Component
@RequiredArgsConstructor
public class PaymentNotificationListener {

    private final NotificationOutbox outbox;
    private final UserService userService;
    private final AppProperties appProperties;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        UserDto user = userService.get(event.userId());
        outbox.enqueue(user.id(), NotificationChannel.EMAIL, NotificationTemplate.PAYMENT_SUCCESS,
                user.email(), Map.of(
                        "name", user.fullName(),
                        "seriesName", event.seriesName(),
                        "amount", event.amount().toPlainString(),
                        "currency", event.currency(),
                        "paymentId", event.providerPaymentId(),
                        "appUrl", appProperties.frontendUrl() + "/my/series"));
    }
}
