package com.examprep.file;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * File types identified by their <b>magic bytes</b>. The client-supplied Content-Type
 * and file extension are never trusted. A renamed .html or .svg (which could carry
 * script) is therefore never stored as an "image".
 *
 * <p>SVG is deliberately unsupported: it is XML that can embed JavaScript and would be
 * served from our bucket domain.
 */
@Getter
@RequiredArgsConstructor
public enum DetectedFileType {

    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
    GIF("image/gif", "gif"),
    WEBP("image/webp", "webp"),
    PDF("application/pdf", "pdf");

    /** Number of leading bytes needed to identify every supported type. */
    public static final int SNIFF_LENGTH = 12;

    private final String mimeType;
    private final String extension;

    public boolean isImage() {
        return this != PDF;
    }

    public static Optional<DetectedFileType> detect(byte[] head) {
        if (startsWith(head, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (startsWith(head, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (ascii(head, 0, "GIF87a") || ascii(head, 0, "GIF89a")) {
            return Optional.of(GIF);
        }
        if (ascii(head, 0, "RIFF") && ascii(head, 8, "WEBP")) {
            return Optional.of(WEBP);
        }
        if (ascii(head, 0, "%PDF-")) {
            return Optional.of(PDF);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int... signature) {
        if (data.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean ascii(byte[] data, int offset, String expected) {
        byte[] exp = expected.getBytes(StandardCharsets.US_ASCII);
        return data.length >= offset + exp.length
                && Arrays.equals(data, offset, offset + exp.length, exp, 0, exp.length);
    }
}
