package com.examprep.test;

import com.examprep.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Storefront visibility plus free / paid / batch access rules, against the seeded series. */
class SeriesAccessIntegrationTest extends AbstractIntegrationTest {

    static final String FREE_SERIES = "15000000-0000-7000-8000-000000000001";
    static final String PAID_SERIES = "15000000-0000-7000-8000-000000000002";
    static final String BATCH_SERIES = "15000000-0000-7000-8000-000000000003";
    static final String PAID_TEST = "16000000-0000-7000-8000-000000000002";
    static final String SAMPLE_TEST = "16000000-0000-7000-8000-000000000003";
    static final String BATCH_TEST = "16000000-0000-7000-8000-000000000004";

    @Test
    void storefront_is_public_and_hides_batch_restricted_series() throws Exception {
        mvc.perform(get("/api/v1/public/series").param("examCode", "JEE_MAIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].slug", hasItem("jee-main-full-mock-series-2027")))
                .andExpect(jsonPath("$.data.content[*].slug", not(hasItem("batch-a-weekly-tests"))))
                .andExpect(jsonPath("$.data.content[0].myAccess").doesNotExist());

        mvc.perform(get("/api/v1/public/series/jee-main-full-mock-series-2027"))
                .andExpect(jsonPath("$.data.series.price").value(499.0))
                .andExpect(jsonPath("$.data.tests.length()").value(2))
                .andExpect(jsonPath("$.data.tests[0].title").value("Free Sample Test"));

        mvc.perform(get("/api/v1/public/series/batch-a-weekly-tests")).andExpect(status().isNotFound());
    }

    @Test
    void new_student_free_enrolls_but_must_pay_for_paid_series() throws Exception {
        String student = bearer(registerStudent());

        mvc.perform(post("/api/v1/series/" + FREE_SERIES + "/enroll").header("Authorization", student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("FREE"))
                .andExpect(jsonPath("$.data.active").value(true));
        // Idempotent.
        mvc.perform(post("/api/v1/series/" + FREE_SERIES + "/enroll").header("Authorization", student))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/series/" + PAID_SERIES + "/enroll").header("Authorization", student))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_REQUIRED"));

        mvc.perform(get("/api/v1/tests/" + PAID_TEST).header("Authorization", student))
                .andExpect(jsonPath("$.data.hasAccess").value(false))
                .andExpect(jsonPath("$.data.availability").value("OPEN"));
        mvc.perform(get("/api/v1/tests/" + SAMPLE_TEST).header("Authorization", student))
                .andExpect(jsonPath("$.data.hasAccess").value(true))
                .andExpect(jsonPath("$.data.sections[0].questionCount").value(2));

        mvc.perform(get("/api/v1/public/series/jee-main-full-mock-series-2027").header("Authorization", student))
                .andExpect(jsonPath("$.data.series.myAccess.hasAccess").value(false))
                .andExpect(jsonPath("$.data.tests[?(@.free==true)].accessible").value(true))
                .andExpect(jsonPath("$.data.tests[?(@.free==false)].accessible").value(false));

        // Outsiders cannot even see the batch series.
        mvc.perform(post("/api/v1/series/" + BATCH_SERIES + "/enroll").header("Authorization", student))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/tests/" + BATCH_TEST).header("Authorization", student))
                .andExpect(status().isNotFound());
    }

    @Test
    void batch_member_gets_restricted_series_for_free() throws Exception {
        String member = bearer(login(STUDENT, STUDENT_PASSWORD));   // seeded into batch JEE-2027-A

        mvc.perform(get("/api/v1/tests/" + BATCH_TEST).header("Authorization", member))
                .andExpect(jsonPath("$.data.hasAccess").value(true));
        mvc.perform(post("/api/v1/series/" + BATCH_SERIES + "/enroll").header("Authorization", member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("BATCH"));
        mvc.perform(get("/api/v1/me/enrollments").header("Authorization", member))
                .andExpect(jsonPath("$.data[*].seriesId", hasItem(BATCH_SERIES)));
    }

    @Test
    void admin_can_grant_and_revoke_access() throws Exception {
        String admin = bearer(login(ADMIN, ADMIN_PASSWORD));
        String studentToken = registerStudent();
        String userId = body(mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(studentToken)))
                .andReturn()).at("/data/id").asText();

        mvc.perform(post("/api/v1/admin/series/" + PAID_SERIES + "/enrollments").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("userId", userId))))
                .andExpect(jsonPath("$.data.source").value("ADMIN"));
        mvc.perform(get("/api/v1/tests/" + PAID_TEST).header("Authorization", bearer(studentToken)))
                .andExpect(jsonPath("$.data.hasAccess").value(true));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/admin/series/" + PAID_SERIES + "/enrollments/" + userId)
                        .header("Authorization", admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/tests/" + PAID_TEST).header("Authorization", bearer(studentToken)))
                .andExpect(jsonPath("$.data.hasAccess").value(false));
    }

    @Test
    void only_admins_manage_series_and_batches() throws Exception {
        String teacher = bearer(login(TEACHER, TEACHER_PASSWORD));
        mvc.perform(get("/api/v1/admin/series").header("Authorization", teacher)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/series/" + PAID_SERIES + "/archive").header("Authorization", teacher))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/batches").header("Authorization", teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("code", "X1", "name", "X"))))
                .andExpect(status().isForbidden());
    }
}
