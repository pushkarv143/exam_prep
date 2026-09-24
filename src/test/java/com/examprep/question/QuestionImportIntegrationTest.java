package com.examprep.question;

import com.examprep.question.importer.ImportColumns;
import com.examprep.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuestionImportIntegrationTest extends AbstractIntegrationTest {

    static final String IMPORT = "/api/v1/admin/questions/import";
    static final String HEADER = "examCode,subjectCode,chapter,topic,type,questionText,optionA,optionB,correctAnswer,tags\n";

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "questions.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void template_download() throws Exception {
        mvc.perform(get(IMPORT + "/template").header("Authorization", bearer(login(TEACHER, TEACHER_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("question-import-template.csv")))
                .andExpect(content().string(containsString("examCode,subjectCode")));
    }

    @Test
    void dry_run_validates_everything_and_saves_nothing_then_real_import_saves() throws Exception {
        String teacher = bearer(login(TEACHER, TEACHER_PASSWORD));
        String tag = "imp" + UUID.randomUUID().toString().substring(0, 8);
        String sheet = HEADER
                + "JEE_MAIN,PHY,Kinematics,Projectile Motion,SCQ,Range formula?,$R=u^2/g$,$R=2u/g$,A," + tag + "\n"
                + "jee_main,math,calculus,LIMITS,NUMERICAL,\"$\\lim_{x\\to0} \\frac{\\sin x}{x}$\",,,1," + tag + "\n";

        mvc.perform(multipart(IMPORT).file(csv(sheet)).param("dryRun", "true").header("Authorization", teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.validRows").value(2))
                .andExpect(jsonPath("$.data.importedRows").value(0))
                .andExpect(jsonPath("$.data.dryRun").value(true));
        mvc.perform(get("/api/v1/admin/questions").param("tag", tag).header("Authorization", teacher))
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mvc.perform(multipart(IMPORT).file(csv(sheet)).header("Authorization", teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.importedRows").value(2));
        mvc.perform(get("/api/v1/admin/questions").param("tag", tag).header("Authorization", teacher))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void any_invalid_row_rejects_the_whole_file_with_row_numbers() throws Exception {
        String teacher = bearer(login(TEACHER, TEACHER_PASSWORD));
        String tag = "bad" + UUID.randomUUID().toString().substring(0, 8);
        String sheet = HEADER
                + "JEE_MAIN,PHY,Kinematics,Projectile Motion,SCQ,Fine row,x,y,A," + tag + "\n"
                + "JEE_MAIN,PHY,Kinematics,No Such Topic,SCQ,Unknown topic,x,y,A," + tag + "\n"
                + "JEE_MAIN,PHY,Kinematics,Projectile Motion,SCQ,Wrong answer letter,x,y,Z," + tag + "\n"
                + "XYZ,PHY,Kinematics,Projectile Motion,SCQ,Unknown exam,x,y,A," + tag + "\n";

        mvc.perform(multipart(IMPORT).file(csv(sheet)).header("Authorization", teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validRows").value(1))
                .andExpect(jsonPath("$.data.importedRows").value(0))
                .andExpect(jsonPath("$.data.errors.length()").value(3))
                .andExpect(jsonPath("$.data.errors[0].row").value(3))
                .andExpect(jsonPath("$.data.errors[0].message").value("Unknown topic: No Such Topic"))
                .andExpect(jsonPath("$.data.errors[1].row").value(4))
                .andExpect(jsonPath("$.data.errors[2].message").value("Unknown exam code: XYZ"));

        mvc.perform(get("/api/v1/admin/questions").param("tag", tag).header("Authorization", teacher))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void auto_create_catalog_adds_missing_chapter_and_topic() throws Exception {
        String teacher = bearer(login(TEACHER, TEACHER_PASSWORD));
        String chapter = "Rotational Motion " + UUID.randomUUID().toString().substring(0, 6);
        String sheet = HEADER + "JEE_MAIN,PHY," + chapter + ",Moment of Inertia,SCQ,MI of ring?,$MR^2$,$MR^2/2$,A,auto\n";

        mvc.perform(multipart(IMPORT).file(csv(sheet)).param("autoCreateCatalog", "true")
                        .header("Authorization", teacher))
                .andExpect(jsonPath("$.data.importedRows").value(1));

        mvc.perform(get("/api/v1/public/catalog/exams/JEE_MAIN/tree"))
                .andExpect(content().string(containsString(chapter)))
                .andExpect(content().string(containsString("Moment of Inertia")));
    }

    @Test
    void missing_required_columns_and_empty_sheets_are_rejected() throws Exception {
        String teacher = bearer(login(TEACHER, TEACHER_PASSWORD));
        mvc.perform(multipart(IMPORT).file(csv("examCode,type\nJEE_MAIN,SCQ\n")).header("Authorization", teacher))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message", containsString("Missing required columns")));

        mvc.perform(multipart(IMPORT).file(csv(ImportColumns.TEMPLATE_CSV.lines().findFirst().orElseThrow() + "\n"))
                        .header("Authorization", teacher))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("The sheet has no data rows"));
    }
}
