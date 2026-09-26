package com.examprep.auth.dto;

import com.examprep.user.dto.UserDto;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Returned by register/login/refresh. The client sends {@code accessToken} as a Bearer
 * header and calls {@code /auth/refresh} with {@code refreshToken} on TOKEN_EXPIRED.
 * The refresh token is single-use: always store the new one returned by refresh.
 *
 * <p>When the account has 2FA, login returns only {@code mfaRequired=true} and a 5-minute
 * {@code mfaToken}. Exchange it with the 6-digit code at {@code POST /auth/login/mfa}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        UserDto user,
        Boolean mfaRequired,
        String mfaToken) {

    public static AuthResponse mfaChallenge(String mfaToken) {
        return new AuthResponse(null, null, null, null, null, null, true, mfaToken);
    }
}
