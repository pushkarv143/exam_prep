package com.examprep.security.mfa;

import com.examprep.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TOTP enrolment, two-step login, replay protection, recovery codes and session revocation. */
class MfaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void enrol_then_login_needs_a_code_and_codes_cannot_be_replayed() throws Exception {
        String email = "mfa" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String admin = bearer(login(ADMIN, ADMIN_PASSWORD));
        mvc.perform(post("/api/v1/admin/users").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "Two Factor", "email", email,
                                "password", "Secret123", "roles", List.of("REVIEWER")))))
                .andExpect(status().isCreated());
        String token = bearer(login(email, "Secret123"));

        // Setup + confirm with a code computed from the returned secret.
        JsonNode setup = body(mvc.perform(post("/api/v1/me/mfa/setup").header("Authorization", token))
                .andExpect(status().isOk()).andReturn());
        byte[] secret = base32Decode(setup.at("/data/secret").asText());
        assertThat(setup.at("/data/otpauthUri").asText()).startsWith("otpauth://totp/");

        mvc.perform(post("/api/v1/me/mfa/confirm").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"000000\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("MFA_INVALID_CODE"));
        JsonNode confirmed = body(mvc.perform(post("/api/v1/me/mfa/confirm").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code(secret, -1) + "\"}"))
                .andExpect(status().isOk()).andReturn());
        String recovery = confirmed.at("/data/recoveryCodes/0").asText();
        assertThat(confirmed.at("/data/recoveryCodes").size()).isEqualTo(MfaService.RECOVERY_CODES);

        // Login now returns a challenge, not tokens.
        JsonNode challenge = body(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("identifier", email, "password", "Secret123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfaRequired").value(true))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andReturn());
        String mfaToken = challenge.at("/data/mfaToken").asText();

        String current = code(secret, 0);
        JsonNode tokens = body(mvc.perform(post("/api/v1/auth/login/mfa").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("mfaToken", mfaToken, "code", current))))
                .andExpect(status().isOk()).andReturn());
        String mfaAccess = "Bearer " + tokens.at("/data/accessToken").asText();
        mvc.perform(get("/api/v1/me/access").header("Authorization", mfaAccess))
                .andExpect(jsonPath("$.data.mfaVerified").value(true))
                .andExpect(jsonPath("$.data.mfaEnabled").value(true));

        // The challenge is single-use, and the same code cannot be used again (replay).
        String mfaToken2 = body(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("identifier", email, "password", "Secret123"))))
                .andReturn()).at("/data/mfaToken").asText();
        mvc.perform(post("/api/v1/auth/login/mfa").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("mfaToken", mfaToken, "code", current))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login/mfa").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("mfaToken", mfaToken2, "code", current))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("MFA_INVALID_CODE"));

        // A recovery code works exactly once.
        mvc.perform(post("/api/v1/auth/login/mfa").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("mfaToken", mfaToken2, "code", recovery))))
                .andExpect(status().isOk());
        String mfaToken3 = body(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("identifier", email, "password", "Secret123"))))
                .andReturn()).at("/data/mfaToken").asText();
        mvc.perform(post("/api/v1/auth/login/mfa").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("mfaToken", mfaToken3, "code", recovery))))
                .andExpect(status().isUnauthorized());

        // "Log out all devices" kills the MFA session's token immediately.
        mvc.perform(post("/api/v1/me/sessions/revoke-all").header("Authorization", mfaAccess))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/access").header("Authorization", mfaAccess))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_REVOKED"));
        Integer logins = jdbc.queryForObject("select count(*) from audit_log where actor_email = ? and action = 'auth.login' "
                + "and outcome = 'SUCCESS'", Integer.class, email);
        assertThat(logins).isGreaterThanOrEqualTo(2);
    }

    /** The code at the current step plus {@code offset} steps (-1 = previous, still accepted). */
    private static String code(byte[] secret, int offset) {
        return Totp.code(secret, Totp.step(Instant.now()) + offset, Totp.DIGITS);
    }

    private static byte[] base32Decode(String s) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : s.toCharArray()) {
            buffer = (buffer << 5) | alphabet.indexOf(c);
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
