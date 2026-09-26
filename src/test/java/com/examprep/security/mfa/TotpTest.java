package com.examprep.security.mfa;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    /** RFC 6238 appendix B test secret (SHA-1). */
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matches_rfc_6238_test_vectors() {
        // RFC 6238, 8-digit values for T = 59, 1111111109, 1234567890, 2000000000 (SHA-1)
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(59)), 8)).isEqualTo("94287082");
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1111111109)), 8)).isEqualTo("07081804");
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1234567890)), 8)).isEqualTo("89005924");
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(2000000000)), 8)).isEqualTo("69279037");
    }

    @Test
    void accepts_codes_within_one_step_of_drift_and_rejects_older_ones() {
        Instant now = Instant.ofEpochSecond(1_700_000_000);
        long step = Totp.step(now);
        String previous = Totp.code(RFC_SECRET, step - 1, Totp.DIGITS);
        String tooOld = Totp.code(RFC_SECRET, step - 2, Totp.DIGITS);

        assertThat(Totp.verify(RFC_SECRET, previous, now, 0)).hasValue(step - 1);
        assertThat(Totp.verify(RFC_SECRET, tooOld, now, 0)).isEmpty();
    }

    @Test
    void a_used_step_cannot_be_replayed() {
        Instant now = Instant.ofEpochSecond(1_700_000_000);
        String code = Totp.code(RFC_SECRET, Totp.step(now), Totp.DIGITS);

        OptionalLong first = Totp.verify(RFC_SECRET, code, now, 0);
        assertThat(first).isPresent();
        assertThat(Totp.verify(RFC_SECRET, code, now, first.getAsLong())).isEmpty();
    }

    @Test
    void rejects_malformed_codes() {
        Instant now = Instant.now();
        assertThat(Totp.verify(RFC_SECRET, "12345", now, 0)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, "abcdef", now, 0)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, null, now, 0)).isEmpty();
    }

    @Test
    void base32_and_otpauth_uri_are_authenticator_compatible() {
        assertThat(Totp.base32("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");   // RFC 4648
        String uri = Totp.otpauthUri("ExamPrep", "admin@examprep.local", RFC_SECRET);
        assertThat(uri).startsWith("otpauth://totp/ExamPrep:admin%40examprep.local?secret=")
                .contains("issuer=ExamPrep").contains("digits=6").contains("period=30");
    }
}
