package com.examprep.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc / Swagger UI configuration. It is served at {@code /swagger-ui.html}.
 * The "Authorize" button accepts the access token returned by {@code /api/v1/auth/login}.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "ExamPrep API", version = "v1",
                description = "Online test-series platform for JEE / NEET"),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
