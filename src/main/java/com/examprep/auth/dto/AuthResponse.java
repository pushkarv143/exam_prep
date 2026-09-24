package com.examprep.auth.dto;

import com.examprep.user.dto.UserDto;

import java.time.Instant;

/**
 * Returned by register/login/refresh. The client sends {@code accessToken} as a Bearer
 * header and calls {@code /auth/refresh} with {@code refreshToken} on TOKEN_EXPIRED.
 * The refresh token is single-use: always store the new one returned by refresh.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        UserDto user) {
}
