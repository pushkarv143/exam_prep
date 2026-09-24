package com.examprep.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(max = 128) String token,
        @NotBlank @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE) String newPassword) {
}
