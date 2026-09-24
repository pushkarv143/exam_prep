package com.examprep.security.jwt;

import java.time.Instant;

public record IssuedToken(String token, String tokenId, Instant expiresAt) {
}
