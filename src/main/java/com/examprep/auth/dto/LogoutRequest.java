package com.examprep.auth.dto;

/** The optional refresh token is revoked together with the current access token. */
public record LogoutRequest(String refreshToken) {
}
