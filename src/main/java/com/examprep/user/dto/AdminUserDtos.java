package com.examprep.user.dto;

import com.examprep.auth.dto.PasswordPolicy;
import com.examprep.user.entity.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public final class AdminUserDtos {

    private AdminUserDtos() {
    }

    /** Admins create staff (and, if needed, student) accounts directly with an initial password. */
    public record CreateUserRequest(
            @NotBlank @Size(min = 2, max = 150) String fullName,
            @NotBlank @Email @Size(max = 255) String email,
            @Pattern(regexp = "^[6-9]\\d{9}$", message = "must be a valid 10-digit Indian mobile number") String phone,
            @NotBlank @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE) String password,
            @NotEmpty @Size(max = 10) Set<@NotBlank @Size(max = 32) String> roles) {
    }

    public record UpdateStatusRequest(@NotNull UserStatus status) {
    }

    /**
     * @param subjectIds subjects a subject-scoped role (TEACHER) may author; ignored for other roles.
     *                   null = leave the current scopes unchanged.
     */
    public record UpdateRolesRequest(@NotEmpty @Size(max = 10) Set<@NotBlank @Size(max = 32) String> roles,
                                     @Size(max = 50) Set<UUID> subjectIds) {
    }
}
