package com.examprep.auth.event;

import java.util.UUID;

/**
 * Carries the one-time reset link. It is only passed in memory to the notification
 * module and is never persisted in plain text.
 */
public record PasswordResetRequestedEvent(UUID userId, String email, String fullName, String resetUrl,
                                          long expiresInMinutes) {

    @Override
    public String toString() {
        // Keep the secret link out of accidental log statements.
        return "PasswordResetRequestedEvent[userId=" + userId + "]";
    }
}
