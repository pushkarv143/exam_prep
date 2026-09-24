package com.examprep.notification.listener;

import com.examprep.auth.event.PasswordResetRequestedEvent;
import com.examprep.auth.event.UserRegisteredEvent;
import com.examprep.common.config.AppProperties;
import com.examprep.notification.entity.NotificationChannel;
import com.examprep.notification.entity.NotificationTemplate;
import com.examprep.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Turns auth domain events into emails. The listener runs only AFTER the publishing
 * transaction commits, so a rolled-back registration never sends a welcome mail. It
 * also runs asynchronously so the HTTP response does not wait for SMTP.
 */
@Component
@RequiredArgsConstructor
public class AuthNotificationListener {

    private final NotificationService notificationService;
    private final AppProperties appProperties;

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onUserRegistered(UserRegisteredEvent event) {
        notificationService.send(event.userId(), NotificationChannel.EMAIL, NotificationTemplate.WELCOME,
                event.email(), Map.of("name", event.fullName(), "appUrl", appProperties.frontendUrl()));
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        notificationService.send(event.userId(), NotificationChannel.EMAIL, NotificationTemplate.PASSWORD_RESET,
                event.email(), Map.of(
                        "name", event.fullName(),
                        "resetUrl", event.resetUrl(),
                        "expiresInMinutes", event.expiresInMinutes()));
    }
}
