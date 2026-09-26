package com.examprep.rbac.controller;

import com.examprep.common.api.ApiResponse;
import com.examprep.rbac.dto.RbacDtos;
import com.examprep.rbac.service.PermissionService;
import com.examprep.rbac.service.RoleAdminService;
import com.examprep.security.AppSecurityProperties;
import com.examprep.security.AuthUser;
import com.examprep.security.mfa.MfaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Tag(name = "Roles & permissions")
@RestController
@RequiredArgsConstructor
public class RbacController {

    private final RoleAdminService roles;
    private final PermissionService permissions;
    private final MfaService mfa;
    private final AppSecurityProperties securityProperties;

    @Operation(summary = "What I may do: roles, permissions, subject scope, 2FA state (drives the admin UI)")
    @GetMapping("/api/v1/me/access")
    public ApiResponse<RbacDtos.AccessDto> myAccess(@AuthenticationPrincipal AuthUser user) {
        Set<UUID> scope = permissions.subjectScope(user).orElse(null);
        return ApiResponse.ok(new RbacDtos.AccessDto(user.id(), new TreeSet<>(user.roles()),
                new TreeSet<>(permissions.permissionsOf(user.roles())), user.isStaff(), scope != null,
                scope == null ? Set.of() : scope, mfa.isEnabled(user.id()), user.mfaVerified(),
                securityProperties.mfa().enforceForStaff()));
    }

    @Operation(summary = "Permission catalogue grouped by module")
    @PreAuthorize("@perm.has('role.view')")
    @GetMapping("/api/v1/admin/permissions")
    public ApiResponse<List<RbacDtos.PermissionGroup>> catalogue() {
        return ApiResponse.ok(roles.catalogue());
    }

    @Operation(summary = "All roles with their permissions and user counts")
    @PreAuthorize("@perm.any('role.view', 'user.roles')")
    @GetMapping("/api/v1/admin/roles")
    public ApiResponse<List<RbacDtos.RoleDto>> list() {
        return ApiResponse.ok(roles.list());
    }

    @Operation(summary = "One role")
    @PreAuthorize("@perm.has('role.view')")
    @GetMapping("/api/v1/admin/roles/{roleName}")
    public ApiResponse<RbacDtos.RoleDto> get(@PathVariable String roleName) {
        return ApiResponse.ok(roles.get(roleName));
    }

    @Operation(summary = "Create a custom role")
    @PreAuthorize("@perm.has('role.manage')")
    @PostMapping("/api/v1/admin/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RbacDtos.RoleDto> create(@AuthenticationPrincipal AuthUser user,
                                                @Valid @RequestBody RbacDtos.CreateRoleRequest body) {
        return ApiResponse.ok(roles.create(body, user));
    }

    @Operation(summary = "Rename a role or change its permissions (may need approval)")
    @PreAuthorize("@perm.has('role.manage')")
    @PutMapping("/api/v1/admin/roles/{roleName}")
    public ApiResponse<RbacDtos.RoleDto> update(@AuthenticationPrincipal AuthUser user, @PathVariable String roleName,
                                                @Valid @RequestBody RbacDtos.UpdateRoleRequest body) {
        return ApiResponse.ok(roles.update(roleName, body, user));
    }

    @Operation(summary = "Delete an unused custom role")
    @PreAuthorize("@perm.has('role.manage')")
    @DeleteMapping("/api/v1/admin/roles/{roleName}")
    public ApiResponse<Void> delete(@PathVariable String roleName) {
        roles.delete(roleName);
        return ApiResponse.ok();
    }
}
