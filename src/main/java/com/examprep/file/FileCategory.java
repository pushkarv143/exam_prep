package com.examprep.file;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * What an upload is for. The category decides the key prefix, the visibility, the size
 * limit and the allowed types.
 *
 * <p>Objects under {@code public/} are world-readable (bucket policy / CDN) and are
 * cached forever. Keys are unique UUIDs, so content never changes under a URL.
 * Objects under {@code private/} are only reachable via short-lived presigned URLs.
 */
@Getter
@RequiredArgsConstructor
public enum FileCategory {

    QUESTION_IMAGE("public/questions", true, 2L * 1024 * 1024, true, false),
    THUMBNAIL("public/thumbnails", true, 2L * 1024 * 1024, true, false),
    SOLUTION_PDF("private/solutions", false, 20L * 1024 * 1024, false, true);

    private final String prefix;
    private final boolean publicRead;
    private final long maxBytes;
    private final boolean imagesAllowed;
    private final boolean pdfAllowed;

    public boolean allows(DetectedFileType type) {
        return type.isImage() ? imagesAllowed : pdfAllowed;
    }
}
