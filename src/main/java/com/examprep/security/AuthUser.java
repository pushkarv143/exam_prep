package com.examprep.security;

import com.examprep.user.entity.Roles;

import java.security.Principal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * The authenticated principal, built from a verified access token without a DB lookup.
 * Inject it into controllers with {@code @AuthenticationPrincipal AuthUser user}.
 *
 * <p>Roles are role codes. Fine-grained rights are permissions, resolved per request by
 * {@code PermissionService} (cached), never trusted from the token.
 */
public record AuthUser(UUID id, String email, Set<String> roles, String sessionId, String tokenId,
                       Instant tokenExpiresAt, boolean mfaVerified) implements Principal {

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    /** Holds at least one role other than STUDENT. */
    public boolean isStaff() {
        return Roles.isStaff(roles);
    }

    @Override
    public String getName() {
        return id.toString();
    }
}
