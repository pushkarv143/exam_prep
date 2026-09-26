package com.examprep.rbac.service;

import com.examprep.approval.ApprovalSpec;
import com.examprep.approval.MakerChecker;
import com.examprep.audit.service.AuditContext;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.exception.NotFoundException;
import com.examprep.rbac.dto.RbacDtos;
import com.examprep.security.AuthUser;
import com.examprep.user.entity.Role;
import com.examprep.user.entity.Roles;
import com.examprep.user.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Role and permission administration. Rules:
 * <ul>
 *   <li>SUPER_ADMIN (all permissions) and STUDENT (none; students must never get admin
 *       rights) cannot have their permissions edited. Built-in roles cannot be deleted.</li>
 *   <li>No privilege escalation: a non-super-admin can only add permissions they hold.</li>
 *   <li>Permission edits can require maker-checker approval (policy {@code role.update}).</li>
 *   <li>Every change is audited with before/after and invalidates the permission cache on all
 *       instances. Users keep their sessions: permissions are resolved per request.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleAdminService {

    private final RoleRepository roles;
    private final PermissionService permissions;
    private final MakerChecker makerChecker;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<RbacDtos.PermissionGroup> catalogue() {
        Map<String, List<RbacDtos.PermissionDto>> groups = new LinkedHashMap<>();
        jdbc.query("select code, module, description, sensitive from permissions order by display_order, code",
                rs -> {
                    groups.computeIfAbsent(rs.getString("module"), m -> new java.util.ArrayList<>())
                            .add(new RbacDtos.PermissionDto(rs.getString("code"), rs.getString("module"),
                                    rs.getString("description"), rs.getBoolean("sensitive")));
                });
        return groups.entrySet().stream().map(e -> new RbacDtos.PermissionGroup(e.getKey(), e.getValue())).toList();
    }

    @Transactional(readOnly = true)
    public List<RbacDtos.RoleDto> list() {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : roles.countUsersByRole()) {
            counts.put((String) row[0], ((Number) row[1]).longValue());
        }
        return roles.findAllWithPermissions().stream().map(r -> toDto(r, counts.getOrDefault(r.getName(), 0L))).toList();
    }

    @Transactional(readOnly = true)
    public RbacDtos.RoleDto get(String name) {
        Role role = load(name);
        Long count = jdbc.queryForObject("select count(*) from user_roles where role_id = ?", Long.class, role.getId());
        return toDto(role, count == null ? 0 : count);
    }

    @Transactional
    public RbacDtos.RoleDto create(RbacDtos.CreateRoleRequest req, AuthUser actor) {
        if (roles.findByName(req.name()).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "A role named " + req.name() + " already exists");
        }
        Set<String> perms = new TreeSet<>(req.permissions() == null ? Set.of() : req.permissions());
        if (req.copyFrom() != null && !req.copyFrom().isBlank()) {
            perms.addAll(permissions.permissionsOfRole(load(req.copyFrom()).getName()));
        }
        validatePermissions(perms, actor);
        Role role = new Role();
        role.setName(req.name());
        role.setDisplayName(req.displayName().trim());
        role.setDescription(req.description());
        role.setSubjectScoped(req.subjectScoped());
        role.setStaff(true);
        role.setPermissions(perms);
        roles.saveAndFlush(role);
        permissions.invalidate();
        RbacDtos.RoleDto dto = toDto(role, 0);
        AuditContext.action("role.create");
        AuditContext.entity("ROLE", role.getName());
        AuditContext.change(null, dto);
        log.info("Role {} created by {}", role.getName(), actor.id());
        return dto;
    }

    @Transactional
    public RbacDtos.RoleDto update(String name, RbacDtos.UpdateRoleRequest req, AuthUser actor) {
        Role role = load(name);
        boolean permissionChange = req.permissions() != null
                && !new TreeSet<>(req.permissions()).equals(new TreeSet<>(role.getPermissions()));
        if (permissionChange && (role.isAllPermissions() || Roles.STUDENT.equals(role.getName()))) {
            throw new BusinessException(ErrorCode.ROLE_READ_ONLY,
                    role.getName() + " permissions are fixed (" + (role.isAllPermissions() ? "all" : "none") + ")");
        }
        if (permissionChange) {
            validatePermissions(req.permissions(), actor);
            makerChecker.guard(ApprovalSpec.of("role.update", "ROLE", role.getName(),
                    "Change permissions of role " + role.getName(),
                    Map.of("roleName", role.getName(), "request", req)));
        }
        RbacDtos.RoleDto before = get(name);
        role.setDisplayName(req.displayName().trim());
        role.setDescription(req.description());
        if (req.subjectScoped() != null && !role.isSystem()) {
            role.setSubjectScoped(req.subjectScoped());
        }
        if (permissionChange) {
            role.getPermissions().clear();
            role.getPermissions().addAll(req.permissions());
        }
        roles.saveAndFlush(role);
        permissions.invalidate();
        RbacDtos.RoleDto after = get(name);
        AuditContext.action("role.update");
        AuditContext.entity("ROLE", name);
        AuditContext.change(before, after);
        return after;
    }

    @Transactional
    public void delete(String name) {
        Role role = load(name);
        if (role.isSystem()) {
            throw new BusinessException(ErrorCode.ROLE_READ_ONLY, "Built-in roles cannot be deleted");
        }
        Long users = jdbc.queryForObject("select count(*) from user_roles where role_id = ?", Long.class, role.getId());
        if (users != null && users > 0) {
            throw new BusinessException(ErrorCode.ROLE_IN_USE, "Remove the role from its " + users + " user(s) first");
        }
        RbacDtos.RoleDto before = toDto(role, 0);
        roles.delete(role);
        permissions.invalidate();
        AuditContext.action("role.delete");
        AuditContext.entity("ROLE", name);
        AuditContext.change(before, null);
    }

    private void validatePermissions(Set<String> requested, AuthUser actor) {
        Set<String> unknown = new TreeSet<>(requested);
        unknown.removeAll(permissions.catalogue());
        if (!unknown.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown permission(s): " + unknown);
        }
        if (!actor.hasRole(Roles.SUPER_ADMIN)) {
            Set<String> missing = new TreeSet<>(requested);
            missing.removeAll(permissions.permissionsOf(actor.roles()));
            if (!missing.isEmpty()) {
                throw new BusinessException(ErrorCode.PRIVILEGE_ESCALATION,
                        "You cannot grant permissions you do not have: " + missing);
            }
        }
    }

    private Role load(String name) {
        return roles.findWithPermissionsByName(name).orElseThrow(() -> NotFoundException.of("Role", name));
    }

    private RbacDtos.RoleDto toDto(Role r, long userCount) {
        Set<String> perms = r.isAllPermissions() ? permissions.catalogue() : new TreeSet<>(r.getPermissions());
        return new RbacDtos.RoleDto(r.getId(), r.getName(), r.getDisplayName(), r.getDescription(), r.isSystem(),
                r.isStaff(), r.isAllPermissions(), r.isSubjectScoped(), new TreeSet<>(perms), userCount,
                r.getUpdatedAt());
    }
}
