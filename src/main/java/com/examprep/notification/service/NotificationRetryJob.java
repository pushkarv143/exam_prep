package com.examprep.notification.service;

import com.examprep.notification.entity.Notification;
import com.examprep.notification.entity.NotificationTemplate;
import com.examprep.notification.repository.NotificationRepository;
import com.examprep.notification.sender.OutboundMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * Re-sends FAILED notifications, up to {@link NotificationService#MAX_ATTEMPTS}.
 * Rows are claimed with SKIP LOCKED, so the job is safe to run on every instance.
 * Sensitive templates are excluded because their stored body is redacted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetryJob {

    private static final int BATCH_SIZE = 50;
    private static final List<String> NON_RETRYABLE = Arrays.stream(NotificationTemplate.values())
            .filter(NotificationTemplate::isSensitive)
            .map(Enum::name)
            .toList();

    private final NotificationRepository repository;
    private final NotificationService notificationService;

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    @Transactional
    public void retryFailed() {
        List<Notification> batch = repository.lockRetryBatch(NotificationService.MAX_ATTEMPTS, NON_RETRYABLE,
                BATCH_SIZE);
        if (batch.isEmpty()) {
            return;
        }
        log.info("Retrying {} failed notifications", batch.size());
        for (Notification n : batch) {
            notificationService.deliver(n, new OutboundMessage(n.getRecipient(), n.getSubject(), n.getBody()));
        }
        // Entities are managed, so changes flush on commit.
    }
}
