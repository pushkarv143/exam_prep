package com.examprep.file;

import com.examprep.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileStorageIntegrationTest extends AbstractIntegrationTest {

    static final String FILES = "/api/v1/admin/files";

    @Autowired
    S3Client s3;

    @BeforeEach
    void ensureBucket() {
        try {
            s3.createBucket(b -> b.bucket(BUCKET));
        } catch (BucketAlreadyOwnedByYouException ignored) {
            // created by an earlier test
        }
    }

    @Test
    void uploads_image_under_public_prefix_with_sniffed_content_type() throws Exception {
        byte[] png = Arrays.copyOf(DetectedFileTypeTest.PNG, 64);
        // The client lies about the type; the server trusts the magic bytes.
        MockMultipartFile file = new MockMultipartFile("file", "../../evil name.txt", "text/plain", png);

        JsonNode res = body(mvc.perform(multipart(FILES).file(file).param("category", "QUESTION_IMAGE")
                        .header("Authorization", bearer(login(TEACHER, TEACHER_PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.key", matchesPattern("^public/questions/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.png$")))
                .andExpect(jsonPath("$.data.contentType").value("image/png"))
                .andReturn());

        String key = res.at("/data/key").asText();
        HeadObjectResponse head = s3.headObject(b -> b.bucket(BUCKET).key(key));
        assertThat(head.contentType()).isEqualTo("image/png");
        assertThat(head.contentLength()).isEqualTo(64);
        assertThat(res.at("/data/url").asText()).endsWith("/" + BUCKET + "/" + key);
    }

    @Test
    void rejects_disguised_and_disallowed_files() throws Exception {
        String teacher = bearer(login(TEACHER, TEACHER_PASSWORD));
        MockMultipartFile fakePng = new MockMultipartFile("file", "x.png", "image/png",
                "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart(FILES).file(fakePng).param("category", "QUESTION_IMAGE")
                        .header("Authorization", teacher))
                .andExpect(status().isUnsupportedMediaType());

        MockMultipartFile pdf = new MockMultipartFile("file", "s.pdf", "application/pdf",
                "%PDF-1.4 hello".getBytes(StandardCharsets.US_ASCII));
        mvc.perform(multipart(FILES).file(pdf).param("category", "QUESTION_IMAGE")
                        .header("Authorization", teacher))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void private_pdf_is_downloadable_only_through_presigned_url() throws Exception {
        String teacher = bearer(login(TEACHER, TEACHER_PASSWORD));
        byte[] pdfBytes = "%PDF-1.4 solution".getBytes(StandardCharsets.US_ASCII);
        JsonNode uploaded = body(mvc.perform(multipart(FILES)
                        .file(new MockMultipartFile("file", "sol.pdf", "application/pdf", pdfBytes))
                        .param("category", "SOLUTION_PDF").header("Authorization", teacher))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.url").doesNotExist())
                .andReturn());
        String key = uploaded.at("/data/key").asText();
        assertThat(key).startsWith("private/solutions/");

        String url = body(mvc.perform(get(FILES + "/presigned-url").param("key", key)
                        .header("Authorization", teacher))
                .andExpect(status().isOk()).andReturn()).at("/data/url").asText();

        HttpResponse<byte[]> download = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(pdfBytes);

        mvc.perform(get(FILES + "/presigned-url").param("key", "../../etc/passwd")
                        .header("Authorization", teacher))
                .andExpect(status().isBadRequest());
    }
}
