package com.examprep.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Small crypto helpers for opaque tokens (password-reset links, receipts, ...). */
public final class Hashing {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Hashing() {
    }

    /** Hex-encoded SHA-256. Opaque tokens are stored only as hashes, never in plain text. */
    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Cryptographically random URL-safe token ({@code bytes * 8} bits of entropy). */
    public static String randomUrlToken(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }
}
