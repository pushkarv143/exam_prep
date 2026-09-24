package com.examprep.security;

import com.examprep.user.entity.RoleName;

import java.security.Principal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * The authenticated principal, built from a verified access token without a DB lookup.
 * Inject it into controllers with {@code @AuthenticationPrincipal AuthUser user}.
 */
public record AuthUser(UUID id, String email, Set<RoleName> roles, String sessionId, String tokenId,
                       Instant tokenExpiresAt) implements Principal {

    public boolean hasRole(RoleName role) {
        return roles.contains(role);
    }

    public boolean isStaff() {
        return hasRole(RoleName.ADMIN) || hasRole(RoleName.TEACHER);
    }

    @Override
    public String getName() {
        return id.toString();
    }
}
