package com.examprep.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/** Security settings bound from {@code app.security.*}. */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record AppSecurityProperties(
        @Valid @NotNull Jwt jwt,
        /* When true, a new STUDENT login invalidates that student's previous session. */
        @DefaultValue("false") boolean singleSessionEnabled,
        @DefaultValue("30m") Duration passwordResetTtl,
        @DefaultValue Cors cors,
        @Valid @NotNull Mfa mfa,
        @DefaultValue AdminIpAllowlist adminIpAllowlist) {

    public record Jwt(
            /* Base64-encoded HMAC-SHA key, >= 256 bits. */
            @NotBlank String secret,
            @DefaultValue("examprep") String issuer,
            @DefaultValue("15m") Duration accessTokenTtl,
            @DefaultValue("7d") Duration refreshTokenTtl) {
    }

    public record Cors(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {
    }

    /**
     * @param encryptionKey   base64 AES-256 key encrypting TOTP secrets at rest (MUST be overridden in prod)
     * @param enforceForStaff when true, staff must have 2FA enabled (and verified in this session) to use
     *                        /api/v1/admin/**; they are asked to enrol first
     * @param issuer          name shown in authenticator apps
     */
    public record Mfa(@NotBlank String encryptionKey,
                      @DefaultValue("false") boolean enforceForStaff,
                      @DefaultValue("ExamPrep") String issuer) {
    }

    /**
     * @param forceDisabled emergency switch: ignore the IP allow-list (e.g. when an admin locked
     *                      everyone out). Set ADMIN_IP_ALLOWLIST_FORCE_DISABLED=true and restart.
     */
    public record AdminIpAllowlist(@DefaultValue("false") boolean forceDisabled) {
    }
}
