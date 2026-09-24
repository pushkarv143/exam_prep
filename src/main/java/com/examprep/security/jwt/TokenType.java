package com.examprep.security.jwt;

/** The {@code typ} claim. It stops a refresh token being used as an access token, and vice versa. */
public enum TokenType {
    ACCESS,
    REFRESH
}
