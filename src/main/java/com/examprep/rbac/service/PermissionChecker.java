package com.examprep.rbac.service;

import com.examprep.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * SpEL entry point for method security:
 * <pre>
 * &#64;PreAuthorize("@perm.has('question.create')")
 * &#64;PreAuthorize("@perm.any('result.view', 'test.view')")
 * </pre>
 * A false result makes Spring throw AuthorizationDeniedException, which becomes 403 FORBIDDEN
 * (and a DENIED row in the audit log for admin endpoints).
 */
@Component("perm")
@RequiredArgsConstructor
public class PermissionChecker {

    private final PermissionService permissions;

    public boolean has(String permission) {
        return permissions.has(currentUser(), permission);
    }

    public boolean any(String... codes) {
        AuthUser user = currentUser();
        return user != null && Arrays.stream(codes).anyMatch(c -> permissions.has(user, c));
    }

    public boolean all(String... codes) {
        return permissions.hasAll(currentUser(), Arrays.asList(codes));
    }

    public static AuthUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthUser u ? u : null;
    }
}
