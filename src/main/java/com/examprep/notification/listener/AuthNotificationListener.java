package com.examprep.notification.listener;

import com.examprep.auth.event.PasswordResetRequestedEvent;
import com.examprep.auth.event.UserRegisteredEvent;
import com.examprep.common.config.AppProperties;
import com.examprep.notification.entity.NotificationChannel;
import com.examprep.notification.entity.NotificationTemplate;
import com.examprep.notification.outbox.NotificationOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Turns auth domain events into emails through the transactional outbox. The listener runs
 * just before the publishing transaction commits and writes an outbox row in it. A
 * rolled-back registration therefore never sends a welcome mail, and a committed one never
 * loses its mail (even if the process dies right after commit). SMTP happens later, off the
 * request thread.
 */
@Component
@RequiredArgsConstructor
public class AuthNotificationListener {

    private final NotificationOutbox outbox;
    private final AppProperties appProperties;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onUserRegistered(UserRegisteredEvent event) {
        outbox.enqueue(event.userId(), NotificationChannel.EMAIL, NotificationTemplate.WELCOME,
                event.email(), Map.of("name", event.fullName(), "appUrl", appProperties.frontendUrl()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        outbox.enqueue(event.userId(), NotificationChannel.EMAIL, NotificationTemplate.PASSWORD_RESET,
                event.email(), Map.of(
                        "name", event.fullName(),
                        "resetUrl", event.resetUrl(),
                        "expiresInMinutes", event.expiresInMinutes()));
    }
}
