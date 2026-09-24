package com.examprep.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for integration tests against real PostgreSQL 16, Redis 7 and MinIO containers.
 *
 * <p>The containers are static and NOT annotated with {@code @Container}. Spring Boot
 * starts the {@code @ServiceConnection} ones once, when the first context is created.
 * MinIO is started in the {@code @DynamicPropertySource} method. All test classes share
 * the cached context, so they pay the startup cost once.
 *
 * <p>{@code disabledWithoutDocker = true}: on a machine without Docker these tests are
 * skipped, not failed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    protected static final String ADMIN = "admin@examprep.local";
    protected static final String ADMIN_PASSWORD = "Admin@123";
    protected static final String TEACHER = "teacher@examprep.local";
    protected static final String TEACHER_PASSWORD = "Teacher@123";
    protected static final String STUDENT = "student@examprep.local";
    protected static final String STUDENT_PASSWORD = "Student@123";
    protected static final String BUCKET = "examprep-test";

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @ServiceConnection(name = "redis")
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static final MinIOContainer MINIO = new MinIOContainer(DockerImageName.parse("minio/minio:latest"));

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        MINIO.start();   // no-op if already running
        registry.add("app.storage.s3.endpoint", MINIO::getS3URL);
        registry.add("app.storage.s3.access-key", MINIO::getUserName);
        registry.add("app.storage.s3.secret-key", MINIO::getPassword);
        registry.add("app.storage.s3.bucket", () -> BUCKET);
        registry.add("app.storage.s3.public-base-url", () -> MINIO.getS3URL() + "/" + BUCKET);
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;

    protected String login(String identifier, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("identifier", identifier, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return body(result).at("/data/accessToken").asText();
    }

    /** Registers a brand-new student and returns their access token. */
    protected String registerStudent() throws Exception {
        String email = "s" + java.util.UUID.randomUUID().toString().substring(0, 12) + "@example.com";
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "Test Student", "email", email,
                                "password", "Secret123"))))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).at("/data/accessToken").asText();
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }
}
