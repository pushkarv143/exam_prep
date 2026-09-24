package com.examprep.user.dto;

import com.examprep.auth.dto.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE) String newPassword) {
}
