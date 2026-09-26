package com.examprep.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Second login step: the challenge token from {@code /auth/login} plus a TOTP or recovery code. */
public record MfaLoginRequest(@NotBlank @Size(max = 2048) String mfaToken, @NotBlank @Size(max = 20) String code) {
}
