package com.examprep.attempt;

import com.examprep.attempt.event.AttemptSubmittedEvent;
import com.examprep.attempt.service.AutoSubmitScheduler;
import com.examprep.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The full test-taking engine against real Postgres and Redis. Every test uses fresh students. */
@RecordApplicationEvents
class AttemptFlowIntegrationTest extends AbstractIntegrationTest {

    static final String SAMPLE_TEST = "16000000-0000-7000-8000-000000000001";   // free, 30 min, 1 attempt
    static final String PAID_TEST = "16000000-0000-7000-8000-000000000002";
    static final String Q1 = "14000000-0000-7000-8000-000000000001";   // SCQ, key B
    static final String Q3 = "14000000-0000-7000-8000-000000000003";   // NUMERICAL
    static final String Q6 = "14000000-0000-7000-8000-000000000006";   // MCQ
    static final String Q7 = "14000000-0000-7000-8000-000000000007";
    static final String Q8 = "14000000-0000-7000-8000-000000000008";

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    AutoSubmitScheduler scheduler;
    @Autowired
    ApplicationEvents events;

    private JsonNode start(String token, String testId) throws Exception {
        return body(mvc.perform(post("/api/v1/tests/" + testId + "/attempts").header("Authorization", token))
                .andExpect(status().isOk()).andReturn()).at("/data");
    }

    private JsonNode autosave(String token, String attemptId, List<Map<String, Object>> changes) throws Exception {
        return body(mvc.perform(put("/api/v1/attempts/" + attemptId + "/answers").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("changes", changes))))
                .andExpect(status().isOk()).andReturn()).at("/data");
    }

    private static Map<String, Object> change(String q, long seq, Map<String, Object> answer, boolean marked) {
        return answer == null
                ? Map.of("questionId", q, "seq", seq, "markedForReview", marked, "timeSpentSeconds", 30, "visits", 1)
                : Map.of("questionId", q, "seq", seq, "answer", answer, "markedForReview", marked,
                "timeSpentSeconds", 30, "visits", 1);
    }

    @Test
    void start_is_idempotent_and_paper_never_leaks_answers() throws Exception {
        String student = bearer(registerStudent());
        JsonNode session = start(student, SAMPLE_TEST);
        String attemptId = session.at("/attemptId").asText();

        assertThat(session.at("/status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(session.at("/remainingSeconds").asLong()).isBetween(1790L, 1800L);
        assertThat(session.at("/paper/totalQuestions").asInt()).isEqualTo(10);
        assertThat(session.toString()).doesNotContain("answerKey", "solution", "correct");

        assertThat(start(student, SAMPLE_TEST).at("/attemptId").asText()).isEqualTo(attemptId);   // resume
        mvc.perform(get("/api/v1/attempts/" + attemptId).header("Authorization", bearer(registerStudent())))
                .andExpect(status().isNotFound());
    }

    @Test
    void autosave_validates_orders_by_seq_and_submit_flushes_once() throws Exception {
        String student = bearer(registerStudent());
        String attemptId = start(student, SAMPLE_TEST).at("/attemptId").asText();

        JsonNode saved = autosave(student, attemptId, List.of(
                change(Q1, 1, Map.of("options", List.of("b")), false),
                change(Q6, 2, Map.of("options", List.of("C", "a")), true),
                change(Q3, 3, Map.of("value", "20"), false),
                change(Q7, 4, Map.of("options", List.of("A", "B")), false),    // two options on SCQ
                change(UUID.randomUUID().toString(), 5, null, false)));
        assertThat(saved.at("/applied").asInt()).isEqualTo(3);
        assertThat(saved.at("/rejected").size()).isEqualTo(2);

        // A late retry with an older seq must not overwrite the newer answer.
        assertThat(autosave(student, attemptId, List.of(change(Q1, 0, Map.of("options", List.of("A")), false)))
                .at("/applied").asInt()).isZero();

        mvc.perform(get("/api/v1/attempts/" + attemptId).header("Authorization", student))
                .andExpect(jsonPath("$.data.answers['" + Q1 + "'].answer.options[0]").value("B"))
                .andExpect(jsonPath("$.data.answers['" + Q6 + "'].state").value("ANSWERED_AND_MARKED"))
                .andExpect(jsonPath("$.data.lastSeq").value(3));

        mvc.perform(post("/api/v1/attempts/" + attemptId + "/submit").header("Authorization", student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.submitType").value("MANUAL"))
                .andExpect(jsonPath("$.data.answeredCount").value(3))
                .andExpect(jsonPath("$.data.markedCount").value(1));
        mvc.perform(post("/api/v1/attempts/" + attemptId + "/submit").header("Authorization", student))
                .andExpect(jsonPath("$.data.answeredCount").value(3));                  // idempotent

        Integer rows = jdbc.queryForObject("SELECT count(*) FROM attempt_answers WHERE attempt_id = ?::uuid",
                Integer.class, attemptId);
        assertThat(rows).isEqualTo(3);
        String stored = jdbc.queryForObject("SELECT answer::text FROM attempt_answers WHERE attempt_id = ?::uuid "
                + "AND question_id = ?::uuid", String.class, attemptId, Q6);
        assertThat(stored).isEqualTo("{\"options\": [\"A\", \"C\"]}");
        assertThat(events.stream(AttemptSubmittedEvent.class)
                .filter(e -> e.attemptId().toString().equals(attemptId)).count()).isEqualTo(1);

        mvc.perform(put("/api/v1/attempts/" + attemptId + "/answers").header("Authorization", student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("changes", List.of()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ATTEMPT_NOT_IN_PROGRESS"));
        mvc.perform(post("/api/v1/tests/" + SAMPLE_TEST + "/attempts").header("Authorization", student))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NO_ATTEMPTS_LEFT"));
    }

    @Test
    void expired_attempt_rejects_autosave_and_is_auto_submitted() throws Exception {
        String student = bearer(registerStudent());
        String attemptId = start(student, SAMPLE_TEST).at("/attemptId").asText();
        autosave(student, attemptId, List.of(change(Q1, 1, Map.of("options", List.of("B")), false)));

        // Simulate the clock running out: move the deadline into the past everywhere.
        Instant past = Instant.now().minusSeconds(600);
        jdbc.update("UPDATE attempts SET deadline_at = ? WHERE id = ?::uuid", java.sql.Timestamp.from(past), attemptId);
        redis.opsForHash().put("attempt:" + attemptId + ":meta", "deadline", String.valueOf(past.toEpochMilli()));
        redis.opsForZSet().add("attempts:deadlines", attemptId, past.toEpochMilli());

        mvc.perform(put("/api/v1/attempts/" + attemptId + "/answers").header("Authorization", student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("changes", List.of()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ATTEMPT_EXPIRED"));

        scheduler.sweepRedis();

        assertThat(jdbc.queryForObject("SELECT status || '/' || submit_type FROM attempts WHERE id = ?::uuid",
                String.class, attemptId)).isEqualTo("SUBMITTED/AUTO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM attempt_answers WHERE attempt_id = ?::uuid",
                Integer.class, attemptId)).isEqualTo(1);   // the autosaved answer survived
    }

    @Test
    void attempt_any_n_limit_and_tab_switch_auto_submit() throws Exception {
        String admin = bearer(login(ADMIN, ADMIN_PASSWORD));
        String testId = body(mvc.perform(post("/api/v1/admin/tests").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "examId", "10000000-0000-7000-8000-000000000001",
                                "seriesId", "15000000-0000-7000-8000-000000000001",
                                "title", "Any-1 " + UUID.randomUUID(), "pattern", "CUSTOM", "durationMinutes", 10,
                                "shuffleQuestions", true, "shuffleOptions", true))))
                .andReturn()).at("/data/test/id").asText();
        String sectionId = body(mvc.perform(post("/api/v1/admin/tests/" + testId + "/sections")
                        .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Maths", "maxQuestionsToAttempt", 1))))
                .andReturn()).at("/data/id").asText();
        mvc.perform(post("/api/v1/admin/tests/" + testId + "/sections/" + sectionId + "/questions")
                .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("questionIds", List.of(Q7, Q8)))));
        mvc.perform(post("/api/v1/admin/tests/" + testId + "/publish").header("Authorization", admin))
                .andExpect(status().isOk());

        String student = bearer(registerStudent());
        JsonNode session = start(student, testId);
        String attemptId = session.at("/attemptId").asText();
        // A resume returns the identical shuffled order.
        assertThat(start(student, testId).at("/paper").toString()).isEqualTo(session.at("/paper").toString());

        assertThat(autosave(student, attemptId, List.of(change(Q7, 1, Map.of("options", List.of("C")), false)))
                .at("/applied").asInt()).isEqualTo(1);
        JsonNode over = autosave(student, attemptId, List.of(change(Q8, 2, Map.of("options", List.of("B")), false)));
        assertThat(over.at("/applied").asInt()).isZero();
        assertThat(over.at("/rejected/0/reason").asText()).contains("only 1 answered");

        mvc.perform(post("/api/v1/attempts/" + attemptId + "/events").header("Authorization", student)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("events",
                                List.of(Map.of("type", "TAB_SWITCH"), Map.of("type", "TAB_SWITCH"),
                                        Map.of("type", "TAB_SWITCH"), Map.of("type", "TAB_SWITCH"))))))
                .andExpect(jsonPath("$.data.tabSwitchCount").value(4))
                .andExpect(jsonPath("$.data.autoSubmitted").value(true));
        assertThat(jdbc.queryForObject("SELECT string_agg(event_type, ',' ORDER BY id) FROM attempt_events "
                + "WHERE attempt_id = ?::uuid", String.class, attemptId)).contains("TAB_SWITCH", "AUTO_SUBMITTED");
    }

    @Test
    void cannot_start_without_access() throws Exception {
        mvc.perform(post("/api/v1/tests/" + PAID_TEST + "/attempts").header("Authorization", bearer(registerStudent())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED_NOT_ENROLLED"));
    }
}
