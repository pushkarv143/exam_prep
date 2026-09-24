package com.examprep.user.dto;

import com.examprep.user.entity.RoleName;
import com.examprep.user.entity.UserStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Public view of a user. Never contains the password hash. */
public record UserDto(
        UUID id,
        String email,
        String phone,
        String fullName,
        Set<RoleName> roles,
        UserStatus status,
        boolean emailVerified,
        String avatarUrl,
        String targetExamCode,
        String city,
        String state,
        Instant lastLoginAt,
        Instant createdAt) {
}
