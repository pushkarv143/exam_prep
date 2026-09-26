package com.examprep.security.jwt;

/**
 * The {@code typ} claim. It stops a token of one kind being used as another: a refresh token as
 * an access token, or the short-lived 2FA challenge token (MFA) as either.
 */
public enum TokenType {
    ACCESS,
    REFRESH,
    MFA
}
