package com.examprep.catalog;

import com.examprep.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogIntegrationTest extends AbstractIntegrationTest {

    static final String JEE_MAIN_ID = "10000000-0000-7000-8000-000000000001";
    static final String KINEMATICS_ID = "12000000-0000-7000-8000-000000000001";
    static final String TREE = "/api/v1/public/catalog/exams/jee_main/tree";
    static final String KINEMATICS_TOPICS =
            "$.data.subjects[?(@.code=='PHY')].chapters[?(@.name=='Kinematics')].topics[*].name";

    @Test
    void public_endpoints_need_no_auth_and_return_seeded_tree() throws Exception {
        mvc.perform(get("/api/v1/public/catalog/exams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].code", hasItem("JEE_MAIN")))
                .andExpect(jsonPath("$.data[*].code", hasItem("NEET")));

        mvc.perform(get(TREE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exam.code").value("JEE_MAIN"))
                .andExpect(jsonPath(KINEMATICS_TOPICS, hasItem("Projectile Motion")));
    }

    @Test
    void new_topic_is_visible_immediately_despite_cache() throws Exception {
        mvc.perform(get(TREE)).andExpect(status().isOk());      // warm the Redis cache

        String name = "Relative Motion " + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/api/v1/admin/catalog/topics")
                        .header("Authorization", bearer(login(TEACHER, TEACHER_PASSWORD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "chapterId", KINEMATICS_ID, "name", name, "displayOrder", 9))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value(name));

        mvc.perform(get(TREE))
                .andExpect(status().isOk())
                .andExpect(jsonPath(KINEMATICS_TOPICS, hasItem(name)));
    }

    @Test
    void deactivated_topic_disappears_from_public_tree_but_not_admin_tree() throws Exception {
        String admin = bearer(login(ADMIN, ADMIN_PASSWORD));
        String name = "Temp Topic " + UUID.randomUUID().toString().substring(0, 8);
        String topicId = body(mvc.perform(post("/api/v1/admin/catalog/topics")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("chapterId", KINEMATICS_ID, "name", name))))
                .andExpect(status().isCreated()).andReturn()).at("/data/id").asText();

        mvc.perform(put("/api/v1/admin/catalog/topics/" + topicId)
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name, "displayOrder", 0, "active", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        mvc.perform(get(TREE))
                .andExpect(jsonPath(KINEMATICS_TOPICS, org.hamcrest.Matchers.not(hasItem(name))));
        mvc.perform(get("/api/v1/admin/catalog/exams/" + JEE_MAIN_ID + "/tree").header("Authorization", admin))
                .andExpect(jsonPath(KINEMATICS_TOPICS, hasItem(name)));
    }

    @Test
    void only_admin_may_create_exams_and_subjects() throws Exception {
        mvc.perform(post("/api/v1/admin/catalog/exams")
                        .header("Authorization", bearer(login(TEACHER, TEACHER_PASSWORD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("code", "CUET", "name", "CUET UG"))))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/admin/catalog/subjects")
                        .header("Authorization", bearer(login(ADMIN, ADMIN_PASSWORD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("examId", JEE_MAIN_ID, "code", "phy",
                                "name", "Physics again"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void unknown_exam_is_404_and_students_cannot_use_admin_catalog() throws Exception {
        mvc.perform(get("/api/v1/public/catalog/exams/NOPE/tree"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/catalog/exams")
                        .header("Authorization", bearer(login(STUDENT, STUDENT_PASSWORD))))
                .andExpect(status().isForbidden());
    }
}
