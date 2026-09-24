package com.examprep.auth.event;

import java.util.UUID;

/** Published after a student registers. Consumed by notification (welcome email) and later by analytics. */
public record UserRegisteredEvent(UUID userId, String email, String fullName) {
}
