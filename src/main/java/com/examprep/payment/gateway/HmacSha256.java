package com.examprep.payment.gateway;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/** HMAC-SHA256 hex signatures (Razorpay's scheme) with a constant-time comparison. */
public final class HmacSha256 {

    private HmacSha256() {
    }

    public static String hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    /** Constant-time, so response timing cannot reveal how many leading characters matched. */
    public static boolean matches(String secret, String data, String providedHex) {
        if (secret == null || secret.isEmpty() || providedHex == null) {
            return false;
        }
        byte[] expected = hex(secret, data).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = providedHex.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }
}
