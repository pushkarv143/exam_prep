package com.examprep.notification.service;

import com.examprep.notification.entity.Notification;
import com.examprep.notification.entity.NotificationChannel;
import com.examprep.notification.entity.NotificationStatus;
import com.examprep.notification.entity.NotificationTemplate;
import com.examprep.notification.repository.NotificationRepository;
import com.examprep.notification.sender.NotificationSender;
import com.examprep.notification.sender.OutboundMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Renders a template, records it in {@code notifications}, and delivers it through the
 * matching {@link NotificationSender}.
 *
 * <p>Delivery runs <b>outside</b> any DB transaction (the SMTP call may take seconds).
 * The row is saved PENDING first, then updated to SENT or FAILED. Callers invoke this
 * from {@code @Async} event listeners, so request threads never wait on SMTP.
 */
@Slf4j
@Service
public class NotificationService {

    public static final int MAX_ATTEMPTS = 3;

    private final NotificationRepository repository;
    private final Map<NotificationChannel, NotificationSender> senders = new EnumMap<>(NotificationChannel.class);
    private final Clock clock;

    public NotificationService(NotificationRepository repository, List<NotificationSender> senderBeans, Clock clock) {
        this.repository = repository;
        this.clock = clock;
        senderBeans.forEach(s -> senders.put(s.channel(), s));
    }

    public void send(UUID userId, NotificationChannel channel, NotificationTemplate template, String recipient,
                     Map<String, ?> variables) {
        String subject = template.renderSubject(variables);
        String body = template.renderBody(variables);

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setChannel(channel);
        notification.setTemplate(template);
        notification.setRecipient(recipient);
        notification.setSubject(subject);
        notification.setBody(template.isSensitive() ? NotificationTemplate.REDACTED : body);
        notification = repository.save(notification);

        deliver(notification, new OutboundMessage(recipient, subject, body));
        repository.save(notification);
    }

    /**
     * Attempts delivery and records the outcome on the entity. The caller persists it.
     * The message is passed separately because the stored body may be redacted.
     */
    void deliver(Notification notification, OutboundMessage message) {
        NotificationSender sender = senders.get(notification.getChannel());
        notification.setAttemptCount(notification.getAttemptCount() + 1);
        if (sender == null) {
            markFailed(notification, "No sender configured for channel " + notification.getChannel());
            return;
        }
        try {
            sender.send(message);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now(clock));
            notification.setLastError(null);
        } catch (RuntimeException e) {
            log.warn("Notification {} ({}) delivery failed: {}", notification.getId(), notification.getTemplate(),
                    e.getMessage());
            markFailed(notification, e.getMessage());
        }
    }

    private static void markFailed(Notification notification, String error) {
        notification.setStatus(NotificationStatus.FAILED);
        notification.setLastError(error == null ? "unknown" : error.substring(0, Math.min(error.length(), 1000)));
    }
}
