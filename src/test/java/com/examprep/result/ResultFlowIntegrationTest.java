package com.examprep.result;

import com.examprep.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Submit → Redis Stream → consumer-group evaluation → results, ranks, solutions, analytics,
 * against real Postgres and Redis. It uses its own freshly created test so ranks are
 * deterministic regardless of other test classes.
 */
class ResultFlowIntegrationTest extends AbstractIntegrationTest {

    static final String Q1 = "14000000-0000-7000-8000-000000000001";   // SCQ key B, +4/-1
    static final String Q3 = "14000000-0000-7000-8000-000000000003";   // NUMERICAL 20, +4/0
    static final String Q6 = "14000000-0000-7000-8000-000000000006";   // MCQ key A,C, +4/-2 partial

    @Autowired
    JdbcTemplate jdbc;

    private String createTest(String admin) throws Exception {
        String testId = body(mvc.perform(post("/api/v1/admin/tests").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "examId", "10000000-0000-7000-8000-000000000001",
                                "seriesId", "15000000-0000-7000-8000-000000000001",
                                "title", "Ranking IT " + UUID.randomUUID(), "pattern", "CUSTOM",
                                "durationMinutes", 30))))
                .andReturn()).at("/data/test/id").asText();
        String sectionId = body(mvc.perform(post("/api/v1/admin/tests/" + testId + "/sections")
                        .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Mixed"))))
                .andReturn()).at("/data/id").asText();
        mvc.perform(post("/api/v1/admin/tests/" + testId + "/sections/" + sectionId + "/questions")
                .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("questionIds", List.of(Q1, Q3, Q6)))));
        mvc.perform(post("/api/v1/admin/tests/" + testId + "/publish").header("Authorization", admin))
                .andExpect(status().isOk());
        return testId;
    }

    /** Starts, answers and submits. Returns the attempt id. */
    private String takeTest(String token, String testId, List<Map<String, Object>> answers) throws Exception {
        String attemptId = body(mvc.perform(post("/api/v1/tests/" + testId + "/attempts").header("Authorization", token))
                .andExpect(status().isOk()).andReturn()).at("/data/attemptId").asText();
        List<Map<String, Object>> changes = new ArrayList<>();
        long seq = 1;
        for (Map<String, Object> a : answers) {
            changes.add(Map.of("questionId", a.get("q"), "seq", seq++, "answer", a.get("answer"),
                    "timeSpentSeconds", 20, "visits", 1));
        }
        mvc.perform(put("/api/v1/attempts/" + attemptId + "/answers").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("changes", changes))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/attempts/" + attemptId + "/submit").header("Authorization", token))
                .andExpect(status().isOk());
        return attemptId;
    }

    /** Evaluation is asynchronous (stream consumer): poll like the frontend does. */
    private JsonNode awaitResult(String token, String attemptId) throws Exception {
        for (int i = 0; i < 100; i++) {
            JsonNode r = body(mvc.perform(get("/api/v1/attempts/" + attemptId + "/result").header("Authorization", token))
                    .andExpect(status().isOk()).andReturn()).at("/data");
            if ("READY".equals(r.at("/status").asText())) {
                return r;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Result not ready for " + attemptId);
    }

    private static Map<String, Object> ans(String q, Object answer) {
        return Map.of("q", q, "answer", answer);
    }

    @Test
    void full_flow_scores_ranks_ties_solutions_leaderboard_and_final_ranks() throws Exception {
        String admin = bearer(login(ADMIN, ADMIN_PASSWORD));
        String testId = createTest(admin);

        String topper = bearer(registerStudent());
        String zero = bearer(registerStudent());
        String tieA = bearer(registerStudent());
        String tieB = bearer(registerStudent());

        String a1 = takeTest(topper, testId, List.of(ans(Q1, Map.of("options", List.of("B"))),
                ans(Q3, Map.of("value", "20")), ans(Q6, Map.of("options", List.of("A", "C")))));       // 12
        String a2 = takeTest(zero, testId, List.of(ans(Q1, Map.of("options", List.of("A"))),
                ans(Q6, Map.of("options", List.of("A")))));                                             // -1 + 1 = 0
        String a3 = takeTest(tieA, testId, List.of(ans(Q1, Map.of("options", List.of("B"))),
                ans(Q6, Map.of("options", List.of("A", "B")))));                                        // 4 - 2 = 2
        String a4 = takeTest(tieB, testId, List.of(ans(Q1, Map.of("options", List.of("B"))),
                ans(Q6, Map.of("options", List.of("B", "A")))));                                        // 2

        JsonNode r1 = awaitResult(topper, a1);
        assertThat(r1.at("/score").decimalValue()).isEqualByComparingTo("12");
        assertThat(r1.at("/maxScore").decimalValue()).isEqualByComparingTo("12");
        assertThat(r1.at("/correct").asInt()).isEqualTo(3);
        assertThat(r1.at("/solutionsAvailable").asBoolean()).isTrue();          // always-open test
        JsonNode r2 = awaitResult(zero, a2);
        assertThat(r2.at("/score").decimalValue()).isEqualByComparingTo("0");
        assertThat(r2.at("/partial").asInt()).isEqualTo(1);
        awaitResult(tieA, a3);
        JsonNode r4 = awaitResult(tieB, a4);

        // Live ranks: ties share a rank (competition ranking), NTA percentile.
        assertThat(awaitResult(topper, a1).at("/rank").asInt()).isEqualTo(1);
        assertThat(r4.at("/rankFinal").asBoolean()).isFalse();
        JsonNode r4Live = awaitResult(tieB, a4);
        assertThat(r4Live.at("/rank").asInt()).isEqualTo(2);
        assertThat(r4Live.at("/percentile").decimalValue()).isEqualByComparingTo("75");
        assertThat(awaitResult(zero, a2).at("/rank").asInt()).isEqualTo(4);

        // Leaderboard.
        mvc.perform(get("/api/v1/tests/" + testId + "/leaderboard").header("Authorization", tieA))
                .andExpect(jsonPath("$.data.totalCandidates").value(4))
                .andExpect(jsonPath("$.data.entries[0].rank").value(1))
                .andExpect(jsonPath("$.data.entries[1].rank").value(2))
                .andExpect(jsonPath("$.data.entries[2].rank").value(2))
                .andExpect(jsonPath("$.data.entries[3].rank").value(4))
                .andExpect(jsonPath("$.data.entries[0].name").value("Test S."))
                .andExpect(jsonPath("$.data.me.rank").value(2));

        // Solutions reveal answers and per-question outcomes.
        mvc.perform(get("/api/v1/attempts/" + a2 + "/solutions").header("Authorization", zero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sections[0].questions[0].correctAnswer.options[0]").value("B"))
                .andExpect(jsonPath("$.data.sections[0].questions[0].yourAnswer.options[0]").value("A"))
                .andExpect(jsonPath("$.data.sections[0].questions[0].outcome").value("INCORRECT"))
                .andExpect(jsonPath("$.data.sections[0].questions[0].marksAwarded").value(-1.0))
                .andExpect(jsonPath("$.data.sections[0].questions[1].outcome").value("UNATTEMPTED"))
                .andExpect(jsonPath("$.data.sections[0].questions[2].outcome").value("PARTIAL"));
        mvc.perform(get("/api/v1/attempts/" + a2 + "/solutions").header("Authorization", topper))
                .andExpect(status().isNotFound());                          // someone else's attempt

        // Comparison: me vs topper vs average (12 + 0 + 2 + 2) / 4 = 4.
        mvc.perform(get("/api/v1/attempts/" + a2 + "/comparison").header("Authorization", zero))
                .andExpect(jsonPath("$.data.candidates").value(4))
                .andExpect(jsonPath("$.data.topper.score").value(12.0))
                .andExpect(jsonPath("$.data.average.score").value(4.0));

        // Final ranks via SQL window functions must agree with the live ranks.
        mvc.perform(post("/api/v1/admin/tests/" + testId + "/rankings/finalize").header("Authorization", admin))
                .andExpect(jsonPath("$.data.rankedCandidates").value(4));
        assertThat(jdbc.queryForList("SELECT rank FROM results WHERE test_id = ?::uuid ORDER BY score DESC, rank",
                Integer.class, testId)).containsExactly(1, 2, 2, 4);
        assertThat(jdbc.queryForList("SELECT percentile FROM results WHERE test_id = ?::uuid ORDER BY score DESC",
                java.math.BigDecimal.class, testId))
                .extracting(java.math.BigDecimal::doubleValue).containsExactly(100.0, 75.0, 75.0, 25.0);
        JsonNode finalR = awaitResult(tieA, a3);
        assertThat(finalR.at("/rankFinal").asBoolean()).isTrue();
        assertThat(finalR.at("/rank").asInt()).isEqualTo(2);

        // Analytics and admin statistics.
        mvc.perform(get("/api/v1/me/analytics").header("Authorization", topper))
                .andExpect(jsonPath("$.data.summary.testsTaken").value(1))
                .andExpect(jsonPath("$.data.trend[0].score").value(12.0));
        mvc.perform(get("/api/v1/admin/tests/" + testId + "/stats").header("Authorization", admin))
                .andExpect(jsonPath("$.data.scores.candidates").value(4))
                .andExpect(jsonPath("$.data.scores.highest").value(12.0))
                .andExpect(jsonPath("$.data.questions[0].attempted").value(4))
                .andExpect(jsonPath("$.data.questions[0].correct").value(3));
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usersByRole.STUDENT").exists());
    }

    @Test
    void hidden_results_and_solutions_for_scheduled_tests_until_window_closes() throws Exception {
        String admin = bearer(login(ADMIN, ADMIN_PASSWORD));
        String testId = createTest(admin);
        // Turn it into a scheduled test that hides results, closing in 30 minutes.
        jdbc.update("UPDATE tests SET show_result_immediately = FALSE, start_at = now() - interval '5 minutes', "
                + "end_at = now() + interval '30 minutes' WHERE id = ?::uuid", testId);

        String student = bearer(registerStudent());
        String attemptId = takeTest(student, testId, List.of(ans(Q1, Map.of("options", List.of("B")))));
        for (int i = 0; i < 100; i++) {
            Integer done = jdbc.queryForObject("SELECT count(*) FROM results WHERE attempt_id = ?::uuid",
                    Integer.class, attemptId);
            if (done != null && done > 0) {
                break;
            }
            Thread.sleep(200);
        }
        mvc.perform(get("/api/v1/attempts/" + attemptId + "/result").header("Authorization", student))
                .andExpect(jsonPath("$.data.status").value("AWAITING_PUBLICATION"))
                .andExpect(jsonPath("$.data.score").doesNotExist());
        mvc.perform(get("/api/v1/attempts/" + attemptId + "/solutions").header("Authorization", student))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/tests/" + testId + "/leaderboard").header("Authorization", student))
                .andExpect(status().isForbidden());
        // Staff can always see.
        mvc.perform(get("/api/v1/attempts/" + attemptId + "/result").header("Authorization", admin))
                .andExpect(jsonPath("$.data.status").value("READY"));
    }

    @Test
    void admin_user_management_revokes_sessions_on_lock() throws Exception {
        String admin = bearer(login(ADMIN, ADMIN_PASSWORD));
        String email = "teacher+" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String userId = body(mvc.perform(post("/api/v1/admin/users").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "fullName", "New Teacher", "email", email, "password", "Teach1234",
                                "roles", List.of("TEACHER")))))
                .andExpect(status().isCreated()).andReturn()).at("/data/id").asText();

        String teacherToken = bearer(login(email, "Teach1234"));
        mvc.perform(get("/api/v1/admin/questions").header("Authorization", teacherToken)).andExpect(status().isOk());

        mvc.perform(patch("/api/v1/admin/users/" + userId + "/status").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LOCKED\"}"))
                .andExpect(jsonPath("$.data.status").value("LOCKED"));
        mvc.perform(get("/api/v1/admin/questions").header("Authorization", teacherToken))
                .andExpect(status().isUnauthorized());                      // session revoked immediately
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("identifier", email, "password", "Teach1234"))))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/admin/users").param("q", email).param("role", "TEACHER").header("Authorization", admin))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }
}
