package com.examprep.question;

import com.examprep.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Review workflow, four-eyes rules and version pinning of tests (Admin Portal A2). */
class ContentStudioIntegrationTest extends AbstractIntegrationTest {

    static final String BASE = "/api/v1/admin/questions";
    static final String TOPIC_1D = "13000000-0000-7000-8000-000000000001";
    static final String REVIEWER = "reviewer@examprep.local";
    static final String CONTENT = "content@examprep.local";
    static final String STAFF_PASSWORD = "Staff@123";

    @Autowired
    JdbcTemplate jdbc;

    private static Map<String, Object> scq(String text, String correct) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "SINGLE_CORRECT");
        body.put("topicId", TOPIC_1D);
        body.put("content", Map.of("text", text, "options", List.of(Map.of("id", "A", "text", "1"),
                Map.of("id", "B", "text", "2"), Map.of("id", "C", "text", "3"), Map.of("id", "D", "text", "4"))));
        body.put("answerKey", Map.of("options", List.of(correct)));
        return body;
    }

    private JsonNode call(String method, String url, String token, Object body, int expectedStatus) throws Exception {
        var builder = switch (method) {
            case "POST" -> post(url);
            case "PUT" -> put(url);
            default -> get(url);
        };
        builder.header("Authorization", token).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.content(json.writeValueAsString(body));
        }
        return body(mvc.perform(builder).andExpect(status().is(expectedStatus)).andReturn());
    }

    private String create(String token, Map<String, Object> body) throws Exception {
        return call("POST", BASE, token, body, 201).at("/data/id").asText();
    }

    private String uuidOf(String email) {
        return jdbc.queryForObject("select id::text from users where email = ?", String.class, email);
    }

    @Test
    void submit_request_changes_resubmit_approve_publish() throws Exception {
        String teacher = bearer(login(TEACHER, TEACHER_PASSWORD));
        String reviewer = bearer(login(REVIEWER, STAFF_PASSWORD));
        String content = bearer(login(CONTENT, STAFF_PASSWORD));
        String id = create(teacher, scq("Workflow " + UUID.randomUUID(), "B"));

        JsonNode submitted = call("POST", BASE + "/" + id + "/submit", teacher, Map.of(), 200).at("/data");
        assertThat(submitted.at("/status").asText()).isEqualTo("IN_REVIEW");
        assertThat(submitted.at("/review/reviewerId").isMissingNode()).isFalse();     // auto-assigned
        assertThat(submitted.at("/review/dueAt").asText()).isNotBlank();

        // Teachers cannot approve; reviewers send it back with a comment.
        call("POST", BASE + "/" + id + "/approve", teacher, Map.of("publish", false), 403);
        JsonNode changes = call("POST", BASE + "/" + id + "/request-changes", reviewer,
                Map.of("comment", "Option D is ambiguous"), 200).at("/data");
        assertThat(changes.at("/status").asText()).isEqualTo("CHANGES_REQUESTED");

        Map<String, Object> fixed = scq("Workflow fixed " + UUID.randomUUID(), "B");
        fixed.put("baseVersion", 1);
        call("PUT", BASE + "/" + id, teacher, fixed, 200);
        JsonNode resubmitted = call("POST", BASE + "/" + id + "/submit", teacher, Map.of(), 200).at("/data");
        assertThat(resubmitted.at("/review/reviewerId").asText()).isEqualTo(uuidOf(REVIEWER));   // same reviewer again

        JsonNode approved = call("POST", BASE + "/" + id + "/approve", reviewer,
                Map.of("comment", "Good now", "publish", false), 200).at("/data/question");
        assertThat(approved.at("/status").asText()).isEqualTo("APPROVED");
        call("POST", BASE + "/" + id + "/publish", reviewer, Map.of(), 403);     // reviewers do not publish

        JsonNode published = call("POST", BASE + "/" + id + "/publish", content, Map.of(), 200).at("/data");
        assertThat(published.at("/question/status").asText()).isEqualTo("PUBLISHED");
        assertThat(published.at("/question/publishedVersion").asInt()).isEqualTo(2);
        assertThat(published.at("/published/previousVersion").isNull()).isTrue();

        mvc.perform(get(BASE + "/" + id + "/activity").header("Authorization", teacher))
                .andExpect(jsonPath("$.data[*].kind", hasItems("CREATED", "SUBMITTED", "CHANGES_REQUESTED", "EDITED",
                        "APPROVED", "PUBLISHED")));
    }

    @Test
    void nobody_approves_their_own_submission() throws Exception {
        String content = bearer(login(CONTENT, STAFF_PASSWORD));
        String id = create(content, scq("Self review " + UUID.randomUUID(), "A"));
        call("POST", BASE + "/" + id + "/submit", content, Map.of("assigneeId", uuidOf(REVIEWER)), 200);
        JsonNode denied = call("POST", BASE + "/" + id + "/approve", content, Map.of("publish", true), 403);
        assertThat(denied.at("/error/code").asText()).isEqualTo("QUESTION_SELF_REVIEW");
    }

    @Test
    void tests_keep_their_version_and_only_same_scoring_fixes_reach_published_tests() throws Exception {
        String admin = bearer(login(ADMIN, ADMIN_PASSWORD));
        String reviewer = bearer(login(REVIEWER, STAFF_PASSWORD));
        String id = create(admin, scq("Pinning " + UUID.randomUUID(), "C"));
        publishViaReview(id, admin, reviewer);                                         // v1 live

        String liveTest = testWith(admin, id);
        String draftTest = testWith(admin, id);
        jdbc.update("update tests set status = 'PUBLISHED' where id = ?::uuid", liveTest);
        assertThat(pin(liveTest, id)).isEqualTo(1);

        // v2: wording only -> both tests move.
        Map<String, Object> wording = scq("Pinning (wording fixed) " + UUID.randomUUID(), "C");
        call("PUT", BASE + "/" + id, admin, wording, 200);
        JsonNode moved = publishViaReview(id, admin, reviewer);
        assertThat(moved.at("/liveTestsUpdated").asInt()).isEqualTo(1);
        assertThat(moved.at("/draftTestsUpdated").asInt()).isEqualTo(1);
        assertThat(pin(liveTest, id)).isEqualTo(2);

        // v3: the answer key changes -> the published test keeps v2, the draft test moves.
        call("PUT", BASE + "/" + id, admin, scq("Pinning, new key", "D"), 200);
        JsonNode kept = publishViaReview(id, admin, reviewer);
        assertThat(kept.at("/liveTestsKept").asInt()).isEqualTo(1);
        assertThat(pin(liveTest, id)).isEqualTo(2);
        assertThat(pin(draftTest, id)).isEqualTo(3);
    }

    /** Submit to reviewer@, approve as reviewer@, publish as the given publisher; returns the publish result. */
    private JsonNode publishViaReview(String id, String publisher, String reviewer) throws Exception {
        call("POST", BASE + "/" + id + "/submit", publisher, Map.of("assigneeId", uuidOf(REVIEWER)), 200);
        call("POST", BASE + "/" + id + "/approve", reviewer, Map.of("publish", false), 200);
        return call("POST", BASE + "/" + id + "/publish", publisher, Map.of("propagate", true), 200).at("/data/published");
    }

    private String testWith(String admin, String questionId) throws Exception {
        JsonNode test = call("POST", "/api/v1/admin/tests", admin, Map.of("examId", "10000000-0000-7000-8000-000000000001",
                "title", "Pins " + UUID.randomUUID().toString().substring(0, 6), "pattern", "CUSTOM",
                "durationMinutes", 30), 201).at("/data/test");
        String testId = test.at("/id").asText();
        String sectionId = call("POST", "/api/v1/admin/tests/" + testId + "/sections", admin,
                Map.of("name", "Physics", "displayOrder", 1), 201).at("/data/id").asText();
        call("POST", "/api/v1/admin/tests/" + testId + "/sections/" + sectionId + "/questions", admin,
                Map.of("questionIds", List.of(questionId)), 200);
        return testId;
    }

    private int pin(String testId, String questionId) {
        Integer v = jdbc.queryForObject("select question_version from test_questions where test_id = ?::uuid "
                + "and question_id = ?::uuid", Integer.class, testId, questionId);
        return v == null ? -1 : v;
    }
}
