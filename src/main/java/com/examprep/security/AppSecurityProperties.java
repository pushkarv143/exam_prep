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
        @DefaultValue Cors cors) {

    public record Jwt(
            /* Base64-encoded HMAC-SHA key, >= 256 bits. */
            @NotBlank String secret,
            @DefaultValue("examprep") String issuer,
            @DefaultValue("15m") Duration accessTokenTtl,
            @DefaultValue("7d") Duration refreshTokenTtl) {
    }

    public record Cors(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {
    }
}
