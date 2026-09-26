package com.examprep.approval;

import com.examprep.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Maker-checker (publishing a test needs a second person), idempotency keys on rank changes,
 * and the audit trail they leave. Runs with approvals enabled (other tests disable them).
 */
@TestPropertySource(properties = "app.approvals.enabled=true")
class MakerCheckerIntegrationTest extends AbstractIntegrationTest {

    private static final String OPERATOR = "operator@examprep.local";
    private static final String CONTENT = "content@examprep.local";
    private static final String STAFF_PASSWORD = "Staff@123";
    private static final String PHYSICS = "11000000-0000-7000-8000-000000000001";
    private static final String Q1 = "14000000-0000-7000-8000-000000000001";

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void publishing_needs_a_second_person_and_leaves_an_audit_trail() throws Exception {
        String operator = bearer(login(OPERATOR, STAFF_PASSWORD));
        String content = bearer(login(CONTENT, STAFF_PASSWORD));
        String testId = publishableTest(content);

        // 1. The operator asks to publish: 202, nothing changes yet.
        JsonNode pending = body(mvc.perform(post("/api/v1/admin/tests/" + testId + "/publish")
                        .header("Authorization", operator).header("X-Reason", "Mock 3 is ready"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.approvalRequired").value(true))
                .andReturn());
        String requestId = pending.at("/data/request/id").asText();
        assertThat(testStatus(testId)).isEqualTo("DRAFT");

        // Asking twice returns the same open request.
        mvc.perform(post("/api/v1/admin/tests/" + testId + "/publish").header("Authorization", operator))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.request.id").value(requestId));

        // 2. The requester cannot approve their own request.
        mvc.perform(post("/api/v1/admin/approvals/" + requestId + "/approve").header("Authorization", operator)
                        .header("Idempotency-Key", "self-" + requestId))
                .andExpect(status().isForbidden());

        // 3. A content manager sees it waiting and approves it: the test is published.
        mvc.perform(get("/api/v1/admin/approvals").param("view", "to-decide").header("Authorization", content))
                .andExpect(jsonPath("$.data.content[?(@.id == '" + requestId + "')].canDecide").value(true));
        mvc.perform(post("/api/v1/admin/approvals/" + requestId + "/approve").header("Authorization", content)
                        .header("Idempotency-Key", "approve-" + requestId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"Checked the paper\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EXECUTED"));
        assertThat(testStatus(testId)).isEqualTo("PUBLISHED");

        // 4. Audit trail: the request, the approval and the actual publish (with a diff).
        List<String> actions = jdbc.queryForList("select action from audit_log where entity_id in (?, ?) "
                + "order by occurred_at", String.class, testId, requestId);
        assertThat(actions).contains("test.publish.requested", "approval.approve", "test.publish");
        String changes = jdbc.queryForObject("select changes::text from audit_log where action = 'test.publish' "
                + "and entity_id = ? order by occurred_at desc limit 1", String.class, testId);
        assertThat(changes).contains("\"path\":\"status\"").contains("PUBLISHED");
        String reason = jdbc.queryForObject("select reason from approval_requests where id = ?::uuid", String.class,
                requestId);
        assertThat(reason).isEqualTo("Mock 3 is ready");
    }

    @Test
    void rejecting_requires_a_comment_and_leaves_the_test_untouched() throws Exception {
        String operator = bearer(login(OPERATOR, STAFF_PASSWORD));
        String content = bearer(login(CONTENT, STAFF_PASSWORD));
        String testId = publishableTest(content);
        String requestId = body(mvc.perform(post("/api/v1/admin/tests/" + testId + "/publish")
                .header("Authorization", operator)).andReturn()).at("/data/request/id").asText();

        mvc.perform(post("/api/v1/admin/approvals/" + requestId + "/reject").header("Authorization", content)
                        .header("Idempotency-Key", "rej-empty-" + requestId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/approvals/" + requestId + "/reject").header("Authorization", content)
                        .header("Idempotency-Key", "rej-" + requestId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"Fix question 2 first\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
        assertThat(testStatus(testId)).isEqualTo("DRAFT");
    }

    @Test
    void rank_changes_need_an_idempotency_key_and_replay_it() throws Exception {
        String admin = bearer(login(ADMIN, ADMIN_PASSWORD));
        jdbc.update("update approval_policies set enabled = false where action = 'result.finalize'");
        try {
            String url = "/api/v1/admin/tests/16000000-0000-7000-8000-000000000001/rankings/finalize";
            mvc.perform(post(url).header("Authorization", admin)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REQUIRED"));

            String key = "finalize-" + UUID.randomUUID();
            MvcResult first = mvc.perform(post(url).header("Authorization", admin).header("Idempotency-Key", key))
                    .andExpect(status().isOk()).andReturn();
            mvc.perform(post(url).header("Authorization", admin).header("Idempotency-Key", key))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Idempotent-Replayed", "true"))
                    .andExpect(jsonPath("$.data.rankedCandidates")
                            .value(body(first).at("/data/rankedCandidates").asInt()));

            // Same key on a different request is refused.
            mvc.perform(post("/api/v1/admin/tests/16000000-0000-7000-8000-000000000002/rankings/finalize")
                            .header("Authorization", admin).header("Idempotency-Key", key))
                    .andExpect(status().isUnprocessableEntity());
        } finally {
            jdbc.update("update approval_policies set enabled = true where action = 'result.finalize'");
        }
    }

    @Test
    void audit_rows_are_append_only() {
        jdbc.update("insert into audit_log (id, occurred_at, action, outcome) values (?, now(), 'test.probe', 'SUCCESS')",
                UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update("update audit_log set action = 'tampered' where action = 'test.probe'"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from audit_log where action = 'test.probe'"))
                .hasMessageContaining("append-only");
    }

    @Test
    void denied_admin_writes_are_audited() throws Exception {
        String teacher = bearer(login(TEACHER, TEACHER_PASSWORD));
        mvc.perform(post("/api/v1/admin/roles").header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"NOPE\",\"displayName\":\"Nope\"}"))
                .andExpect(status().isForbidden());
        Integer denied = jdbc.queryForObject("select count(*) from audit_log where outcome = 'DENIED' "
                + "and actor_email = ? and path = '/api/v1/admin/roles'", Integer.class, TEACHER);
        assertThat(denied).isGreaterThanOrEqualTo(1);
    }

    /** A CUSTOM draft test with one section and one question: publishable. */
    private String publishableTest(String token) throws Exception {
        JsonNode test = body(mvc.perform(post("/api/v1/admin/tests").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("examId", "10000000-0000-7000-8000-000000000001",
                                "title", "Approval test " + UUID.randomUUID().toString().substring(0, 6),
                                "pattern", "CUSTOM", "durationMinutes", 30, "free", true))))
                .andExpect(status().isCreated()).andReturn());
        String testId = test.at("/data/test/id").asText();
        JsonNode section = body(mvc.perform(post("/api/v1/admin/tests/" + testId + "/sections")
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Physics", "subjectId", PHYSICS,
                                "displayOrder", 1))))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(post("/api/v1/admin/tests/" + testId + "/sections/" + section.at("/data/id").asText() + "/questions")
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("questionIds", List.of(Q1)))))
                .andExpect(status().isOk());
        return testId;
    }

    private String testStatus(String testId) {
        return jdbc.queryForObject("select status from tests where id = ?::uuid", String.class, testId);
    }
}
