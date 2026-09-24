package com.examprep.notification.sender;

import com.examprep.common.config.AppProperties;
import com.examprep.notification.entity.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP email sender. In dev, docker-compose runs Mailpit (http://localhost:8025) to
 * catch every mail. With {@code app.notification.email.enabled=false} (tests), messages
 * are only logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(OutboundMessage message) {
        AppProperties.Notification.Email config = appProperties.notification().email();
        if (!config.enabled()) {
            log.info("[email disabled] to={} subject=\"{}\"", message.recipient(), message.subject());
            return;
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(config.from());
        mail.setTo(message.recipient());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        mailSender.send(mail);
    }
}
