package com.examprep.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class RbacDtos {

    private RbacDtos() {
    }

    public record PermissionDto(String code, String module, String description, boolean sensitive) {
    }

    public record PermissionGroup(String module, List<PermissionDto> permissions) {
    }

    public record RoleDto(short id, String name, String displayName, String description, boolean system,
                          boolean staff, boolean allPermissions, boolean subjectScoped, Set<String> permissions,
                          long userCount, Instant updatedAt) {
    }

    public record CreateRoleRequest(
            @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,31}$", message = "must be UPPER_SNAKE_CASE, 2-32 chars")
            String name,
            @NotBlank @Size(max = 80) String displayName,
            @Size(max = 255) String description,
            boolean subjectScoped,
            @Size(max = 200) Set<@NotBlank String> permissions,
            /* optional: start from this role's permissions (added to 'permissions') */
            @Size(max = 32) String copyFrom) {
    }

    public record UpdateRoleRequest(
            @NotBlank @Size(max = 80) String displayName,
            @Size(max = 255) String description,
            Boolean subjectScoped,
            @Size(max = 200) Set<@NotBlank String> permissions) {
    }

    /** What the signed-in user may do. Drives the admin navigation and the portal's 2FA gate. */
    public record AccessDto(UUID userId, Set<String> roles, Set<String> permissions, boolean staff,
                           boolean subjectScoped, Set<UUID> subjectIds, boolean mfaEnabled, boolean mfaVerified,
                           boolean mfaEnforced) {
    }
}
