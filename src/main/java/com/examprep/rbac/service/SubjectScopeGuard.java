package com.examprep.rbac.service;

import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Enforces subject scopes for subject-scoped roles (TEACHER): such users may author and see
 * content only in their assigned subjects. Unscoped staff (content managers, admins) pass.
 */
@Component
@RequiredArgsConstructor
public class SubjectScopeGuard {

    private final PermissionService permissions;

    /** Empty = unrestricted; present = allowed subject ids (possibly none). */
    public Optional<Set<UUID>> scope(AuthUser user) {
        return user == null ? Optional.empty() : permissions.subjectScope(user);
    }

    public void assertCanAuthor(AuthUser user, UUID subjectId) {
        scope(user).ifPresent(allowed -> {
            if (allowed.isEmpty()) {
                throw new BusinessException(ErrorCode.FORBIDDEN,
                        "No subjects are assigned to you yet. Ask an administrator to assign your subjects.");
            }
            if (subjectId == null || !allowed.contains(subjectId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "This subject is outside your assigned subjects");
            }
        });
    }
}
