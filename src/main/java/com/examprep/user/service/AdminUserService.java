package com.examprep.user.service;

import com.examprep.audit.service.AuditContext;
import com.examprep.common.api.PageResponse;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.exception.NotFoundException;
import com.examprep.rbac.service.PermissionService;
import com.examprep.security.AuthUser;
import com.examprep.security.session.SessionService;
import com.examprep.user.dto.AdminUserDtos.CreateUserRequest;
import com.examprep.user.dto.UserDto;
import com.examprep.user.entity.Role;
import com.examprep.user.entity.Roles;
import com.examprep.user.entity.User;
import com.examprep.user.entity.UserStatus;
import com.examprep.user.mapper.UserMapper;
import com.examprep.user.repository.RoleRepository;
import com.examprep.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * User administration. Status and role changes <b>revoke all sessions</b> of the target
 * user, because roles live inside the JWT. Guard rails:
 * <ul>
 *   <li>Admins cannot lock themselves out (deactivate themselves or drop their own SUPER_ADMIN).</li>
 *   <li>No privilege escalation: you can only grant roles whose permissions you already hold,
 *       and only a SUPER_ADMIN can grant SUPER_ADMIN.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessions;
    private final PermissionService permissions;
    private final JdbcTemplate jdbc;
    private final UserMapper mapper;

    public record UserAccess(UUID userId, Set<String> roles, Set<String> permissions, Set<UUID> subjectIds,
                             boolean subjectScoped) {
    }

    @Transactional(readOnly = true)
    public PageResponse<UserDto> search(String q, UserStatus status, String role, Pageable pageable) {
        String query = q == null || q.isBlank() ? null : q.trim();
        return PageResponse.of(users.search(query, status, role, pageable), mapper::toDto);
    }

    @Transactional(readOnly = true)
    public UserDto get(UUID id) {
        return mapper.toDto(load(id));
    }

    @Transactional(readOnly = true)
    public UserAccess access(UUID id) {
        User user = load(id);
        Set<String> roleNames = user.roleNames();
        boolean scoped = !roleNames.isEmpty() && user.getRoles().stream()
                .filter(Role::isStaff).allMatch(r -> r.isSubjectScoped() && !r.isAllPermissions())
                && user.isStaff();
        return new UserAccess(id, new TreeSet<>(roleNames), new TreeSet<>(permissions.permissionsOf(roleNames)),
                permissions.subjectsOf(id), scoped);
    }

    @Transactional
    public UserDto create(AuthUser actor, CreateUserRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailNormalized(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "An account with this email already exists");
        }
        if (req.phone() != null && users.existsByPhone(req.phone())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "An account with this phone already exists");
        }
        Set<Role> resolved = resolve(req.roles());
        assertCanGrant(actor, resolved);
        User user = new User();
        user.setEmail(email);
        user.setPhone(req.phone());
        user.setFullName(req.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setEmailVerified(true);
        user.getRoles().addAll(resolved);
        users.saveAndFlush(user);
        UserDto dto = mapper.toDto(user);
        AuditContext.action("user.create");
        AuditContext.entity("USER", user.getId());
        AuditContext.change(null, dto);
        log.info("Admin created user {} with roles {}", user.getId(), req.roles());
        return dto;
    }

    @Transactional
    public UserDto updateStatus(AuthUser actor, UUID id, UserStatus status) {
        if (actor.id().equals(id) && status != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "You cannot deactivate your own account");
        }
        User user = load(id);
        UserDto before = mapper.toDto(user);
        user.setStatus(status);
        if (status != UserStatus.ACTIVE) {
            sessions.revokeAll(id, null, "account " + status.name().toLowerCase());
        }
        UserDto after = mapper.toDto(user);
        AuditContext.action("user.status");
        AuditContext.entity("USER", id);
        AuditContext.change(before, after);
        return after;
    }

    /**
     * Replaces the user's roles and, if {@code subjectIds} is not null, their subject scopes.
     * Scopes only matter for subject-scoped roles (TEACHER).
     */
    @Transactional
    public UserDto updateRoles(AuthUser actor, UUID id, Set<String> newRoleNames, Set<UUID> subjectIds) {
        if (actor.id().equals(id) && actor.hasRole(Roles.SUPER_ADMIN) && !newRoleNames.contains(Roles.SUPER_ADMIN)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "You cannot remove your own SUPER_ADMIN role");
        }
        User user = load(id);
        Set<Role> resolved = resolve(newRoleNames);
        // Granting: every role that is being ADDED must be within the actor's own rights.
        Set<String> current = user.roleNames();
        assertCanGrant(actor, resolved.stream().filter(r -> !current.contains(r.getName()))
                .collect(Collectors.toSet()));

        Map<String, Object> before = Map.of("roles", new TreeSet<>(current),
                "subjectIds", new TreeSet<>(permissions.subjectsOf(id)));
        user.getRoles().clear();
        user.getRoles().addAll(resolved);
        if (subjectIds != null) {
            replaceScopes(id, subjectIds);
        }
        users.flush();
        sessions.revokeAll(id, null, "roles changed");
        permissions.invalidate();

        UserDto after = mapper.toDto(user);
        AuditContext.action("user.roles");
        AuditContext.entity("USER", id);
        AuditContext.change(before, Map.of("roles", new TreeSet<>(after.roles()),
                "subjectIds", subjectIds == null ? before.get("subjectIds") : new TreeSet<>(subjectIds)));
        return after;
    }

    private void replaceScopes(UUID userId, Set<UUID> subjectIds) {
        if (!subjectIds.isEmpty()) {
            Integer known = jdbc.queryForObject("select count(*) from subjects where id = any (?)", Integer.class,
                    (Object) subjectIds.toArray(new UUID[0]));
            if (known == null || known != subjectIds.size()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown subject in subjectIds");
            }
        }
        jdbc.update("delete from user_subject_scopes where user_id = ?", userId);
        for (UUID subjectId : new HashSet<>(subjectIds)) {
            jdbc.update("insert into user_subject_scopes (user_id, subject_id) values (?, ?)", userId, subjectId);
        }
    }

    /** No privilege escalation: the actor must already hold every permission of the granted roles. */
    private void assertCanGrant(AuthUser actor, Set<Role> granted) {
        if (actor.hasRole(Roles.SUPER_ADMIN)) {
            return;
        }
        for (Role role : granted) {
            if (role.isAllPermissions()) {
                throw new BusinessException(ErrorCode.PRIVILEGE_ESCALATION, "Only a super admin can grant " + role.getName());
            }
            Set<String> missing = new TreeSet<>(permissions.permissionsOfRole(role.getName()));
            missing.removeAll(permissions.permissionsOf(actor.roles()));
            if (!missing.isEmpty()) {
                throw new BusinessException(ErrorCode.PRIVILEGE_ESCALATION,
                        "You cannot grant " + role.getName() + ": it includes permissions you do not have " + missing);
            }
        }
    }

    private Set<Role> resolve(Set<String> names) {
        List<Role> found = roles.findByNameIn(names);
        if (found.size() != names.size()) {
            Set<String> known = found.stream().map(Role::getName).collect(Collectors.toSet());
            Set<String> unknown = new TreeSet<>(names);
            unknown.removeAll(known);
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown role(s): " + unknown);
        }
        return new HashSet<>(found);
    }

    private User load(UUID id) {
        return users.findWithRolesById(id).orElseThrow(() -> NotFoundException.of("User", id));
    }
}
