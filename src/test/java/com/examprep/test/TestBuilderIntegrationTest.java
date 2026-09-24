package com.examprep.test;

import com.examprep.support.AbstractIntegrationTest;
import com.examprep.test.event.TestWindowClosedEvent;
import com.examprep.test.service.TestStatusScheduler;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RecordApplicationEvents
class TestBuilderIntegrationTest extends AbstractIntegrationTest {

    static final String TESTS = "/api/v1/admin/tests";
    static final String JEE_MAIN = "10000000-0000-7000-8000-000000000001";
    static final String PHYSICS = "11000000-0000-7000-8000-000000000001";
    static final String Q_PHY_SCQ_1 = "14000000-0000-7000-8000-000000000001";
    static final String Q_PHY_SCQ_2 = "14000000-0000-7000-8000-000000000002";
    static final String Q_PHY_NUM = "14000000-0000-7000-8000-000000000003";

    @Autowired
    ApplicationEvents events;
    @Autowired
    TestStatusScheduler scheduler;
    @Autowired
    JdbcTemplate jdbc;

    private JsonNode createTest(String token, String pattern, Integer duration, Instant startAt) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("examId", JEE_MAIN);
        body.put("title", "IT " + pattern + " " + UUID.randomUUID().toString().substring(0, 6));
        body.put("pattern", pattern);
        if (duration != null) {
            body.put("durationMinutes", duration);
        }
        if (startAt != null) {
            body.put("startAt", startAt.toString());
        }
        return body(mvc.perform(post(TESTS).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn()).at("/data");
    }

    @Test
    void jee_main_pattern_creates_six_typed_sections_and_enforces_section_type() throws Exception {
        String teacher = bearer(login(TEACHER, TEACHER_PASSWORD));
        JsonNode detail = createTest(teacher, "JEE_MAIN", null, null);
        String testId = detail.at("/test/id").asText();

        assertThat(detail.at("/test/durationMinutes").asInt()).isEqualTo(180);
        assertThat(detail.at("/sections").size()).isEqualTo(6);
        JsonNode physA = detail.at("/sections/0");
        JsonNode physB = detail.at("/sections/1");
        assertThat(physA.at("/questionType").asText()).isEqualTo("SINGLE_CORRECT");
        assertThat(physB.at("/maxQuestionsToAttempt").asInt()).isEqualTo(5);
        assertThat(detail.at("/test/instructions").asText()).contains("attempt any 5 of 10");

        // Numerical question into the MCQ section is rejected.
        mvc.perform(post(TESTS + "/" + testId + "/sections/" + physA.at("/id").asText() + "/questions")
                        .header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("questionIds", List.of(Q_PHY_NUM)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message", containsString("accepts only SINGLE_CORRECT")));

        // SCQs go in with the section's marking (+4/-1). A duplicate is skipped, not an error.
        mvc.perform(post(TESTS + "/" + testId + "/sections/" + physA.at("/id").asText() + "/questions")
                        .header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("questionIds",
                                List.of(Q_PHY_SCQ_1, Q_PHY_SCQ_2, Q_PHY_SCQ_1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.added").value(2))
                .andExpect(jsonPath("$.data.skippedAlreadyInTest[0]").value(Q_PHY_SCQ_1));

        mvc.perform(get(TESTS + "/" + testId).header("Authorization", teacher))
                .andExpect(jsonPath("$.data.test.totalQuestions").value(2))
                .andExpect(jsonPath("$.data.test.totalMarks").value(8))
                .andExpect(jsonPath("$.data.sections[0].questions[0].negativeMarks").value(1));

        // Not publishable: five sections are still empty.
        mvc.perform(get(TESTS + "/" + testId + "/validation").header("Authorization", teacher))
                .andExpect(jsonPath("$.data.publishable").value(false))
                .andExpect(jsonPath("$.data.errors.length()").value(5));
        mvc.perform(post(TESTS + "/" + testId + "/publish").header("Authorization", teacher))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void generate_from_pattern_reports_shortfall_and_strict_mode_adds_nothing() throws Exception {
        String teacher = bearer(login(TEACHER, TEACHER_PASSWORD));
        String testId = createTest(teacher, "JEE_MAIN", null, null).at("/test/id").asText();
        Map<String, Object> mix = Map.of("easyPercent", 30, "mediumPercent", 50, "hardPercent", 20);

        Map<String, Object> strict = new HashMap<>(mix);
        strict.put("strict", true);
        mvc.perform(post(TESTS + "/" + testId + "/generate-from-pattern").header("Authorization", teacher)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(strict)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message", containsString("Not enough questions")));
        mvc.perform(get(TESTS + "/" + testId).header("Authorization", teacher))
                .andExpect(jsonPath("$.data.test.totalQuestions").value(0));

        // The seed bank is tiny, so a lenient run fills what it can and reports the rest.
        JsonNode report = body(mvc.perform(post(TESTS + "/" + testId + "/generate-from-pattern")
                        .header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(mix)))
                .andExpect(status().isOk()).andReturn()).at("/data");
        assertThat(report.at("/added").asInt()).isPositive();
        assertThat(report.at("/shortfalls").size()).isEqualTo(6);
    }

    @Test
    void custom_test_lifecycle_publish_lock_unpublish_and_reorder() throws Exception {
        String teacher = bearer(login(TEACHER, TEACHER_PASSWORD));
        String testId = createTest(teacher, "CUSTOM", 30, null).at("/test/id").asText();
        String sectionId = body(mvc.perform(post(TESTS + "/" + testId + "/sections").header("Authorization", teacher)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "name", "Physics", "subjectId", PHYSICS, "defaultMarks", 3,
                                "defaultNegativeMarks", 1))))
                .andExpect(status().isCreated()).andReturn()).at("/data/id").asText();

        // Rule-based generation from the Physics pool (3 seeded Physics questions).
        mvc.perform(post(TESTS + "/" + testId + "/generate").header("Authorization", teacher)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "rules", List.of(Map.of("sectionId", sectionId, "count", 3))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.added").value(3));

        JsonNode section = body(mvc.perform(get(TESTS + "/" + testId).header("Authorization", teacher))
                .andExpect(jsonPath("$.data.test.totalMarks").value(9))     // section default +3 each
                .andReturn()).at("/data/sections/0/questions");
        List<String> reversed = List.of(section.get(2).at("/id").asText(), section.get(1).at("/id").asText(),
                section.get(0).at("/id").asText());
        mvc.perform(put(TESTS + "/" + testId + "/sections/" + sectionId + "/order").header("Authorization", teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("testQuestionIds", reversed))))
                .andExpect(status().isOk());
        mvc.perform(get(TESTS + "/" + testId).header("Authorization", teacher))
                .andExpect(jsonPath("$.data.sections[0].questions[0].id").value(reversed.get(0)));

        mvc.perform(post(TESTS + "/" + testId + "/publish").header("Authorization", teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        // Structure is locked after publishing.
        mvc.perform(post(TESTS + "/" + testId + "/sections").header("Authorization", teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Extra"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TEST_NOT_EDITABLE"));

        mvc.perform(post(TESTS + "/" + testId + "/unpublish").header("Authorization", teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void scheduler_moves_windowed_test_to_completed_and_announces_it() throws Exception {
        String admin = bearer(login(ADMIN, ADMIN_PASSWORD));
        Instant start = Instant.now().minus(5, ChronoUnit.MINUTES);
        String testId = createTest(admin, "CUSTOM", 30, start).at("/test/id").asText();
        String sectionId = body(mvc.perform(post(TESTS + "/" + testId + "/sections").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name", "S"))))
                .andReturn()).at("/data/id").asText();
        mvc.perform(post(TESTS + "/" + testId + "/sections/" + sectionId + "/questions").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("questionIds", List.of(Q_PHY_SCQ_1)))));

        // The window is already open, so publishing goes straight to LIVE.
        mvc.perform(post(TESTS + "/" + testId + "/publish").header("Authorization", admin))
                .andExpect(jsonPath("$.data.status").value("LIVE"));

        // Close the window (simulating time passing) and let the scheduler run.
        jdbc.update("UPDATE tests SET end_at = now() - interval '1 second' WHERE id = ?::uuid", testId);
        scheduler.advance();

        mvc.perform(get(TESTS + "/" + testId).header("Authorization", admin))
                .andExpect(jsonPath("$.data.test.status").value("COMPLETED"));
        assertThat(events.stream(TestWindowClosedEvent.class).map(TestWindowClosedEvent::testId))
                .contains(UUID.fromString(testId));
    }

    @Test
    void patterns_endpoint_and_student_forbidden() throws Exception {
        mvc.perform(get(TESTS + "/patterns").header("Authorization", bearer(login(TEACHER, TEACHER_PASSWORD))))
                .andExpect(jsonPath("$.data[?(@.code=='NEET')].totalMarks").value(720));
        mvc.perform(get(TESTS).header("Authorization", bearer(login(STUDENT, STUDENT_PASSWORD))))
                .andExpect(status().isForbidden());
    }
}
