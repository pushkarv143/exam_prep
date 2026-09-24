package com.examprep.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param identifier email address or 10-digit mobile number
 */
public record LoginRequest(
        @NotBlank @Size(max = 255) String identifier,
        @NotBlank @Size(max = 72) String password) {
}
