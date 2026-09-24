package com.examprep.file;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * S3-compatible object storage ({@code app.storage.s3.*}).
 *
 * @param endpoint        custom endpoint (MinIO). Leave blank on AWS.
 * @param accessKey       leave blank to use the default AWS credential chain (IAM role, IRSA, env).
 * @param publicBaseUrl   browser-facing base URL for {@code public/} objects, e.g. a CloudFront domain
 * @param pathStyleAccess true for MinIO ({@code host/bucket/key}); false for AWS virtual-host style
 */
@Validated
@ConfigurationProperties(prefix = "app.storage.s3")
public record StorageProperties(
        String endpoint,
        @DefaultValue("ap-south-1") String region,
        String accessKey,
        String secretKey,
        @NotBlank String bucket,
        @NotBlank String publicBaseUrl,
        @DefaultValue("true") boolean pathStyleAccess,
        @DefaultValue("15m") Duration presignTtl) {
}
