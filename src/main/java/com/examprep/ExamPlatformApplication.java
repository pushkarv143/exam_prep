package com.examprep;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the ExamPrep modular monolith.
 *
 * <p>Each top-level package under {@code com.examprep} is a feature module
 * (auth, user, catalog, question, test, attempt, result, ...). Modules talk to each
 * other through services and application events only, never through each other's
 * repositories, so they can be extracted into services later.
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} is excluded because authentication is
 * JWT-based. Without the exclusion Boot would create an in-memory user with a generated
 * password.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class ExamPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamPlatformApplication.class, args);
    }
}
