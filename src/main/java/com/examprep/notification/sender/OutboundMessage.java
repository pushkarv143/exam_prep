package com.examprep.notification.sender;

public record OutboundMessage(String recipient, String subject, String body) {
}
