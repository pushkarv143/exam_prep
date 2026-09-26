package com.examprep.security.jwt;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Verified contents of a JWT.
 *
 * @param tokenId     {@code jti}. Unique per token; used for blacklisting and refresh rotation.
 * @param roles       role codes (data-driven since Admin Portal 2.0, e.g. STUDENT, SUPER_ADMIN).
 * @param sessionId   {@code sid}. Stable for one login session across refreshes.
 * @param generation  {@code gen}. The per-user token generation at issue time. Bumping
 *                    the user's generation in Redis revokes every older token at once
 *                    (password reset, account disabled, "log out everywhere").
 * @param mfaVerified {@code mfa}. The session passed a TOTP check (required for staff when enforced).
 */
public record TokenClaims(String tokenId, UUID userId, String email, Set<String> roles, String sessionId,
                          long generation, boolean mfaVerified, TokenType type, Instant issuedAt,
                          Instant expiresAt) {
}
