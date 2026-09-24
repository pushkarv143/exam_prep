package com.examprep.notification.listener;

import com.examprep.common.config.AppProperties;
import com.examprep.notification.entity.NotificationChannel;
import com.examprep.notification.entity.NotificationTemplate;
import com.examprep.notification.service.NotificationService;
import com.examprep.payment.event.PaymentSucceededEvent;
import com.examprep.user.dto.UserDto;
import com.examprep.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/** Sends the payment receipt email only after the payment transaction has committed. */
@Component
@RequiredArgsConstructor
public class PaymentNotificationListener {

    private final NotificationService notificationService;
    private final UserService userService;
    private final AppProperties appProperties;

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        UserDto user = userService.get(event.userId());
        notificationService.send(user.id(), NotificationChannel.EMAIL, NotificationTemplate.PAYMENT_SUCCESS,
                user.email(), Map.of(
                        "name", user.fullName(),
                        "seriesName", event.seriesName(),
                        "amount", event.amount().toPlainString(),
                        "currency", event.currency(),
                        "paymentId", event.providerPaymentId(),
                        "appUrl", appProperties.frontendUrl() + "/my/series"));
    }
}
