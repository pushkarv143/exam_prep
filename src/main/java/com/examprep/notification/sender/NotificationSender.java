package com.examprep.notification.sender;

import com.examprep.notification.entity.NotificationChannel;

/**
 * Strategy for delivering a message over one channel. To add SMS (MSG91, Twilio) or
 * push (FCM), implement this interface as a Spring bean. {@code NotificationService}
 * discovers all senders automatically.
 */
public interface NotificationSender {

    NotificationChannel channel();

    /**
     * @throws RuntimeException on delivery failure (the notification is marked FAILED and retried)
     */
    void send(OutboundMessage message);
}
