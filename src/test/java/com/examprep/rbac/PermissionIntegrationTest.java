package com.examprep.rbac;

import com.examprep.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Permission-based access control: roles as data, per-request resolution, scopes, escalation guard. */
class PermissionIntegrationTest extends AbstractIntegrationTest {

    private static final String SUPPORT = "support@examprep.local";
    private static final String STAFF_PASSWORD = "Staff@123";
    private static final String PHYSICS = "11000000-0000-7000-8000-000000000001";
    private static final String CHEMISTRY_TOPIC = "13000000-0000-7000-8000-000000000004";
    private static final String PHYSICS_TOPIC = "13000000-0000-7000-8000-000000000001";

    @Test
    void endpoints_require_their_own_permission() throws Exception {
        String support = bearer(login(SUPPORT, STAFF_PASSWORD));
        String teacher = bearer(login(TEACHER, TEACHER_PASSWORD));
        String student = bearer(login(STUDENT, STUDENT_PASSWORD));

        // Support agents see users but not the question bank; teachers the opposite.
        mvc.perform(get("/api/v1/admin/users").header("Authorization", support)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/questions").header("Authorization", support)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/questions").header("Authorization", teacher)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", teacher)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/roles").header("Authorization", teacher)).andExpect(status().isForbidden());

        // Students never pass the admin gate.
        mvc.perform(get("/api/v1/admin/tests").header("Authorization", student)).andExpect(status().isForbidden());

        JsonNode access = body(mvc.perform(get("/api/v1/me/access").header("Authorization", support)).andReturn());
        assertThat(access.at("/data/staff").asBoolean()).isTrue();
        assertThat(access.at("/data/permissions").toString()).contains("support.manage").doesNotContain("question.view");
    }

    @Test
    void editing_a_role_takes_effect_without_logging_in_again() throws Exception {
        String admin = bearer(login(ADMIN, ADMIN_PASSWORD));
        String roleName = "QA_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase().replace('-', '_');
        mvc.perform(post("/api/v1/admin/roles").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", roleName, "displayName", "QA",
                                "permissions", List.of("admin.access")))))
                .andExpect(status().isCreated());

        String email = "qa" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        mvc.perform(post("/api/v1/admin/users").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "QA Person", "email", email,
                                "password", "Secret123", "roles", List.of(roleName)))))
                .andExpect(status().isCreated());
        String qa = bearer(login(email, "Secret123"));
        mvc.perform(get("/api/v1/admin/tests").header("Authorization", qa)).andExpect(status().isForbidden());

        mvc.perform(put("/api/v1/admin/roles/" + roleName).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("displayName", "QA",
                                "permissions", List.of("admin.access", "test.view")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions.length()").value(2));

        // Same token, new rights.
        mvc.perform(get("/api/v1/admin/tests").header("Authorization", qa)).andExpect(status().isOk());
    }

    @Test
    void staff_cannot_grant_permissions_they_do_not_hold() throws Exception {
        String admin = bearer(login(ADMIN, ADMIN_PASSWORD));
        // Give the support agent role management for this test only through a custom role.
        String roleName = "HELPDESK_" + UUID.randomUUID().toString().substring(0, 4).toUpperCase().replace('-', '_');
        mvc.perform(post("/api/v1/admin/roles").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", roleName, "displayName", "Helpdesk",
                                "permissions", List.of("admin.access", "role.view", "role.manage", "user.roles")))))
                .andExpect(status().isCreated());
        String email = "hd" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        mvc.perform(post("/api/v1/admin/users").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "Help Desk", "email", email,
                                "password", "Secret123", "roles", List.of(roleName)))))
                .andExpect(status().isCreated());
        String helpdesk = bearer(login(email, "Secret123"));

        mvc.perform(post("/api/v1/admin/roles").header("Authorization", helpdesk).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "SNEAKY_" + roleName.substring(9),
                                "displayName", "Sneaky", "permissions", List.of("admin.access", "refund.approve")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PRIVILEGE_ESCALATION"));
    }

    @Test
    void subject_scoped_teacher_can_author_only_assigned_subjects() throws Exception {
        String admin = bearer(login(ADMIN, ADMIN_PASSWORD));
        String email = "t" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        JsonNode created = body(mvc.perform(post("/api/v1/admin/users").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "Physics Teacher", "email", email,
                                "password", "Secret123", "roles", List.of("TEACHER")))))
                .andExpect(status().isCreated()).andReturn());
        String userId = created.at("/data/id").asText();
        mvc.perform(put("/api/v1/admin/users/" + userId + "/roles").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("roles", Set.of("TEACHER"), "subjectIds", Set.of(PHYSICS)))))
                .andExpect(status().isOk());
        String teacher = bearer(login(email, "Secret123"));

        mvc.perform(post("/api/v1/admin/questions").header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(scq(PHYSICS_TOPIC))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/admin/questions").header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(scq(CHEMISTRY_TOPIC))))
                .andExpect(status().isForbidden());
    }

    private static Map<String, Object> scq(String topicId) {
        return Map.of("type", "SINGLE_CORRECT", "topicId", topicId,
                "content", Map.of("text", "Scope check " + UUID.randomUUID(), "options", List.of(
                        Map.of("id", "A", "text", "1"), Map.of("id", "B", "text", "2"))),
                "answerKey", Map.of("options", List.of("A")));
    }
}
