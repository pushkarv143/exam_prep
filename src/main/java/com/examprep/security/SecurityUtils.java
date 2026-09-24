package com.examprep.security;

import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/** Static access to the current principal, for code that has no controller parameter (auditing, services). */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<AuthUser> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static Optional<UUID> currentUserId() {
        return currentUser().map(AuthUser::id);
    }

    public static AuthUser requireCurrentUser() {
        return currentUser().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
