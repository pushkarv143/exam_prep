package com.examprep.security.jwt;

import com.examprep.user.entity.RoleName;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Verified contents of a JWT.
 *
 * @param tokenId    {@code jti}. Unique per token; used for blacklisting and refresh rotation.
 * @param sessionId  {@code sid}. Stable for one login session across refreshes.
 * @param generation {@code gen}. The per-user token generation at issue time. Bumping
 *                   the user's generation in Redis revokes every older token at once
 *                   (password reset, account disabled, "log out everywhere").
 */
public record TokenClaims(String tokenId, UUID userId, String email, Set<RoleName> roles, String sessionId,
                          long generation, TokenType type, Instant issuedAt, Instant expiresAt) {
}
