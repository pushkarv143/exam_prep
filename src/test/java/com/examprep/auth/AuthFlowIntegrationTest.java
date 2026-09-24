package com.examprep.auth;

import com.examprep.auth.event.PasswordResetRequestedEvent;
import com.examprep.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end auth flows through the full HTTP, security, Postgres and Redis stack. */
@RecordApplicationEvents
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    ApplicationEvents events;

    @Test
    void register_me_refresh_logout_lifecycle() throws Exception {
        String email = "aspirant+" + UUID.randomUUID() + "@example.com";

        // Register (auto-login).
        JsonNode reg = body(mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "fullName", "Riya Sharma", "email", email.toUpperCase(),
                                "password", "Secret123", "targetExamCode", "JEE_MAIN"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.data.user.roles[0]").value("STUDENT"))
                .andReturn());
        String access = reg.at("/data/accessToken").asText();
        String refresh = reg.at("/data/refreshToken").asText();

        // Authenticated call.
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Riya Sharma"));

        // Refresh rotates both tokens.
        JsonNode rotated = body(mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refreshToken", refresh))))
                .andExpect(status().isOk())
                .andReturn());
        String newAccess = rotated.at("/data/accessToken").asText();
        String newRefresh = rotated.at("/data/refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(refresh);

        // Replaying the old refresh token is rejected (single use).
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refreshToken", refresh))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));

        // Logout blacklists the access token and revokes the refresh token.
        mvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + newAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refreshToken", newRefresh))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_REVOKED"));

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refreshToken", newRefresh))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_with_seeded_student_by_email_and_phone() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "identifier", "Student@ExamPrep.local", "password", "Student@123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.roles[0]").value("STUDENT"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "identifier", "9000000003", "password", "Student@123"))))
                .andExpect(status().isOk());
    }

    @Test
    void login_rejects_wrong_password_and_unknown_user_identically() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "identifier", "student@examprep.local", "password", "wrong-pass1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "identifier", "nobody@example.com", "password", "wrong-pass1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void register_validates_input_and_rejects_duplicates() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "fullName", "X", "email", "not-an-email", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.violations.length()").value(3));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "fullName", "Copy Cat", "email", "student@examprep.local", "password", "Secret123"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void protected_endpoint_requires_token_and_reports_invalid_token() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
    }

    @Test
    void student_cannot_reach_admin_endpoints() throws Exception {
        String access = loginAccessToken("student@examprep.local", "Student@123");
        mvc.perform(get("/api/v1/admin/anything").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void forgot_and_reset_password_revokes_old_sessions() throws Exception {
        String email = "reset+" + UUID.randomUUID() + "@example.com";
        JsonNode reg = body(mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "fullName", "Arjun Mehta", "email", email, "password", "OldPass123"))))
                .andExpect(status().isCreated()).andReturn());
        String oldAccess = reg.at("/data/accessToken").asText();

        mvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk());

        // The token only travels by email, so capture it from the domain event.
        String resetUrl = events.stream(PasswordResetRequestedEvent.class)
                .filter(e -> e.email().equals(email))
                .findFirst().orElseThrow().resetUrl();
        String token = resetUrl.substring(resetUrl.indexOf("token=") + 6);

        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", token, "newPassword", "NewPass456"))))
                .andExpect(status().isOk());

        // Token is single-use.
        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", token, "newPassword", "Another789"))))
                .andExpect(status().isBadRequest());

        // Old session is dead; new password works.
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + oldAccess))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_REVOKED"));
        loginAccessToken(email, "NewPass456");
    }

    @Test
    void forgot_password_for_unknown_email_still_returns_ok() throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "ghost@example.com"))))
                .andExpect(status().isOk());
    }

    private String loginAccessToken(String identifier, String password) throws Exception {
        return login(identifier, password);
    }
}
