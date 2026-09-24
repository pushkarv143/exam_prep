package com.examprep.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Public self-registration. It always creates a STUDENT; staff accounts are created by admins. */
public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 150) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "must be a valid 10-digit Indian mobile number") String phone,
        @NotBlank @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE) String password,
        @Size(max = 32) String targetExamCode) {
}
