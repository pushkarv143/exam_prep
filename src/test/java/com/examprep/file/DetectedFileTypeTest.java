package com.examprep.file;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DetectedFileTypeTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};

    @Test
    void detects_supported_types_from_magic_bytes() {
        assertThat(DetectedFileType.detect(PNG)).contains(DetectedFileType.PNG);
        assertThat(DetectedFileType.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}))
                .contains(DetectedFileType.JPEG);
        assertThat(DetectedFileType.detect(ascii("GIF89a......"))).contains(DetectedFileType.GIF);
        assertThat(DetectedFileType.detect(ascii("RIFF\0\0\0\0WEBPVP8 "))).contains(DetectedFileType.WEBP);
        assertThat(DetectedFileType.detect(ascii("%PDF-1.7\n..."))).contains(DetectedFileType.PDF);
    }

    @Test
    void rejects_disguised_or_unsupported_content() {
        assertThat(DetectedFileType.detect(ascii("<svg onload=alert(1)>"))).isEmpty();
        assertThat(DetectedFileType.detect(ascii("<html><script>"))).isEmpty();
        assertThat(DetectedFileType.detect(ascii("RIFF\0\0\0\0WAVEfmt "))).isEmpty();   // RIFF but not WEBP
        assertThat(DetectedFileType.detect(new byte[]{(byte) 0x89, 'P'})).isEmpty();      // truncated
        assertThat(DetectedFileType.detect(new byte[0])).isEmpty();
    }

    @Test
    void category_rules() {
        assertThat(FileCategory.QUESTION_IMAGE.allows(DetectedFileType.PNG)).isTrue();
        assertThat(FileCategory.QUESTION_IMAGE.allows(DetectedFileType.PDF)).isFalse();
        assertThat(FileCategory.SOLUTION_PDF.allows(DetectedFileType.PDF)).isTrue();
        assertThat(FileCategory.SOLUTION_PDF.allows(DetectedFileType.JPEG)).isFalse();
        assertThat(FileCategory.SOLUTION_PDF.isPublicRead()).isFalse();
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }
}
