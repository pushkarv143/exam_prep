package com.examprep.question;

import com.examprep.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuestionIntegrationTest extends AbstractIntegrationTest {

    static final String TOPIC_1D = "13000000-0000-7000-8000-000000000001";
    /** Seeded question 1 is part of the PUBLISHED sample test. Correct answer: B. */
    static final String SEEDED_Q1 = "14000000-0000-7000-8000-000000000001";
    static final String BASE = "/api/v1/admin/questions";

    private static Map<String, Object> scq(String text, String correct, String tag) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "SINGLE_CORRECT");
        body.put("difficulty", "EASY");
        body.put("topicId", TOPIC_1D);
        body.put("content", Map.of(
                "text", text,
                "options", List.of(Map.of("id", "A", "text", "$1$"), Map.of("id", "B", "text", "$2$"),
                        Map.of("id", "C", "text", "$3$"), Map.of("id", "D", "text", "$4$")),
                "solution", Map.of("text", "Because.")));
        body.put("answerKey", Map.of("options", List.of(correct)));
        body.put("tags", List.of(tag));
        return body;
    }

    private String create(String token, Map<String, Object> body) throws Exception {
        return body(mvc.perform(post(BASE).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn()).at("/data/id").asText();
    }

    @Test
    void teacher_creates_and_finds_question_by_filters_and_text() throws Exception {
        String teacher = bearer(login(TEACHER, TEACHER_PASSWORD));
        String unique = "zebra" + UUID.randomUUID().toString().substring(0, 6);
        String tag = "tag-" + unique;

        String id = create(teacher, scq("How many legs does a " + unique + " have, $n$?", "D", tag));

        mvc.perform(get(BASE + "/" + id).header("Authorization", teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answerKey.options[0]").value("D"))
                .andExpect(jsonPath("$.data.topic.subjectName").value("Physics"))
                .andExpect(jsonPath("$.data.topic.chapterName").value("Kinematics"))
                .andExpect(jsonPath("$.data.marks").value(4))              // SCQ default +4
                .andExpect(jsonPath("$.data.negativeMarks").value(1))      // SCQ default -1
                .andExpect(jsonPath("$.data.usedInPublishedTest").value(false));

        mvc.perform(get(BASE).param("q", unique.toUpperCase()).header("Authorization", teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(id));

        mvc.perform(get(BASE).param("tag", tag).param("topicId", TOPIC_1D).param("mine", "true")
                        .header("Authorization", teacher))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(id)));

        mvc.perform(get(BASE).param("q", "100%_literal").header("Authorization", teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void invalid_answer_key_is_rejected_with_reason() throws Exception {
        Map<String, Object> body = scq("Bad", "A", "x");
        body.put("answerKey", Map.of("options", List.of("A", "B")));
        mvc.perform(post(BASE).header("Authorization", bearer(login(TEACHER, TEACHER_PASSWORD)))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.message").value("SINGLE_CORRECT must have exactly one correct option"));
    }

    @Test
    void answer_key_is_frozen_once_used_in_published_test_but_wording_can_change() throws Exception {
        String admin = bearer(login(ADMIN, ADMIN_PASSWORD));
        mvc.perform(get(BASE + "/" + SEEDED_Q1).header("Authorization", admin))
                .andExpect(jsonPath("$.data.usedInPublishedTest").value(true));

        Map<String, Object> changedKey = scq("A car starts from rest...", "A", "kinematics");
        mvc.perform(put(BASE + "/" + SEEDED_Q1).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(changedKey)))
                .andExpect(status().isConflict());

        Map<String, Object> sameKey = scq("A car starts from rest (typo fixed)...", "b", "kinematics");
        mvc.perform(put(BASE + "/" + SEEDED_Q1).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(sameKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.text").value("A car starts from rest (typo fixed)..."));
    }

    @Test
    void teacher_cannot_modify_admins_question_but_admin_can_archive_any() throws Exception {
        String admin = bearer(login(ADMIN, ADMIN_PASSWORD));
        String teacher = bearer(login(TEACHER, TEACHER_PASSWORD));
        String tag = "own-" + UUID.randomUUID().toString().substring(0, 8);

        String adminQ = create(admin, scq("Admin question", "A", tag));
        mvc.perform(put(BASE + "/" + adminQ).header("Authorization", teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(scq("Hijack", "A", tag))))
                .andExpect(status().isForbidden());

        String teacherQ = create(teacher, scq("Teacher question", "C", tag));
        mvc.perform(delete(BASE + "/" + teacherQ).header("Authorization", admin))
                .andExpect(status().isOk());

        // Archived questions are hidden from default search.
        mvc.perform(get(BASE).param("tag", tag).header("Authorization", admin))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(adminQ)))
                .andExpect(jsonPath("$.data.content[*].id", not(hasItem(teacherQ))));
        mvc.perform(get(BASE).param("tag", tag).param("status", "ARCHIVED").header("Authorization", admin))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(teacherQ)));
    }

    @Test
    void paragraph_with_child_question() throws Exception {
        String teacher = bearer(login(TEACHER, TEACHER_PASSWORD));
        String paragraphId = create(teacher, Map.of(
                "type", "PARAGRAPH", "topicId", TOPIC_1D,
                "content", Map.of("paragraph", "A ball is thrown vertically upward with $u = 20$ m/s...")));

        Map<String, Object> child = scq("Maximum height reached is:", "B", "paragraph");
        child.put("parentId", paragraphId);
        String childId = create(teacher, child);

        mvc.perform(get(BASE).param("parentId", paragraphId).header("Authorization", teacher))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(childId));

        Map<String, Object> badChild = scq("Parent is not a paragraph", "A", "x");
        badChild.put("parentId", childId);
        mvc.perform(post(BASE).header("Authorization", teacher)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(badChild)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("Parent must be a PARAGRAPH question"));
    }

    @Test
    void students_cannot_access_question_bank() throws Exception {
        mvc.perform(get(BASE).header("Authorization", bearer(login(STUDENT, STUDENT_PASSWORD))))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknown_sort_property_is_a_400() throws Exception {
        mvc.perform(get(BASE).param("sort", "nope,desc")
                        .header("Authorization", bearer(login(TEACHER, TEACHER_PASSWORD))))
                .andExpect(status().isBadRequest());
    }
}
