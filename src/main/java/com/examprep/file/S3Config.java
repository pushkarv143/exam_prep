package com.examprep.file;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * AWS SDK v2 clients. The same code targets MinIO locally (endpoint override +
 * path-style) and AWS S3 in production (no endpoint, IAM credentials). Building the
 * clients does not contact the server, so the app starts even when storage is down.
 */
@Configuration
public class S3Config {

    @Bean(destroyMethod = "close")
    public S3Client s3Client(StorageProperties props) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(credentials(props))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(props.pathStyleAccess())
                        .build());
        if (StringUtils.hasText(props.endpoint())) {
            builder.endpointOverride(URI.create(props.endpoint()));
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(StorageProperties props) {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(credentials(props))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(props.pathStyleAccess())
                        .build());
        if (StringUtils.hasText(props.endpoint())) {
            builder.endpointOverride(URI.create(props.endpoint()));
        }
        return builder.build();
    }

    private static AwsCredentialsProvider credentials(StorageProperties props) {
        if (StringUtils.hasText(props.accessKey()) && StringUtils.hasText(props.secretKey())) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
        }
        return DefaultCredentialsProvider.builder().build();
    }
}
