package com.examprep.file;

import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.util.Uuids;
import com.examprep.file.FileDtos.PresignedUrlDto;
import com.examprep.file.FileDtos.StoredFileDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.regex.Pattern;

/**
 * Uploads files to S3/MinIO and issues presigned download URLs.
 *
 * <p>Keys look like {@code public/questions/2026/09/<uuidv7>.png}. They are
 * server-generated, never derived from the client's filename, which rules out path
 * traversal, collisions and PII in URLs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final Pattern VALID_KEY = Pattern.compile("^(public|private)/[a-z0-9_/-]+/[0-9a-f-]{36}\\.[a-z]{3,4}$");

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties props;
    private final Clock clock;

    public StoredFileDto upload(MultipartFile file, FileCategory category) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "File is empty");
        }
        if (file.getSize() > category.getMaxBytes()) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
                    "Max size for " + category + " is " + category.getMaxBytes() / (1024 * 1024) + " MB");
        }
        DetectedFileType type = sniff(file);
        if (!category.allows(type)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE, type + " is not allowed for " + category);
        }

        ZonedDateTime now = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC));
        String key = "%s/%d/%02d/%s.%s".formatted(category.getPrefix(), now.getYear(), now.getMonthValue(),
                Uuids.v7(), type.getExtension());

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(props.bucket())
                .key(key)
                .contentType(type.getMimeType())
                .contentLength(file.getSize())
                .cacheControl(category.isPublicRead() ? "public, max-age=31536000, immutable" : "private, no-store")
                .build();
        try (InputStream in = file.getInputStream()) {
            s3.putObject(request, RequestBody.fromInputStream(in, file.getSize()));
        } catch (SdkException e) {
            log.error("Upload to bucket {} failed: {}", props.bucket(), e.getMessage());
            throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Could not read upload");
        }
        return new StoredFileDto(key, category.isPublicRead() ? publicUrl(key) : null, type.getMimeType(),
                file.getSize());
    }

    /** Short-lived GET URL for any stored object (private files especially). */
    public PresignedUrlDto presignDownload(String key) {
        if (key == null || !VALID_KEY.matcher(key).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid file key");
        }
        try {
            PresignedGetObjectRequest presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(props.presignTtl())
                    .getObjectRequest(GetObjectRequest.builder().bucket(props.bucket()).key(key).build())
                    .build());
            return new PresignedUrlDto(presigned.url().toString(), presigned.expiration());
        } catch (SdkException e) {
            throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE);
        }
    }

    public String publicUrl(String key) {
        String base = props.publicBaseUrl().endsWith("/")
                ? props.publicBaseUrl().substring(0, props.publicBaseUrl().length() - 1)
                : props.publicBaseUrl();
        return base + "/" + key;
    }

    private static DetectedFileType sniff(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(DetectedFileType.SNIFF_LENGTH);
            return DetectedFileType.detect(head).orElseThrow(() -> new BusinessException(
                    ErrorCode.UNSUPPORTED_FILE_TYPE, "Unsupported file. Allowed: PNG, JPEG, GIF, WEBP, PDF"));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Could not read upload");
        }
    }
}
