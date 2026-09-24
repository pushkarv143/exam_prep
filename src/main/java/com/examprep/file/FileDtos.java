package com.examprep.file;

import java.time.Instant;

public final class FileDtos {

    private FileDtos() {
    }

    /**
     * @param key object key. Store it (e.g. in question content) so the file can be re-signed or moved later.
     * @param url permanent public URL for public categories; null for private ones (use a presigned URL)
     */
    public record StoredFileDto(String key, String url, String contentType, long size) {
    }

    public record PresignedUrlDto(String url, Instant expiresAt) {
    }
}
