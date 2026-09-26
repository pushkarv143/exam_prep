package com.examprep.security.mfa;

import com.examprep.audit.service.AuditContext;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.util.Hashing;
import com.examprep.security.AppSecurityProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * TOTP two-factor authentication.
 * <ol>
 *   <li>{@link #beginSetup}: generates and stores an encrypted secret (disabled) and returns the
 *       otpauth URI for the QR code.</li>
 *   <li>{@link #confirmSetup}: verifies the first code, enables 2FA, and returns 10 one-time
 *       recovery codes. Only their SHA-256 hashes are kept.</li>
 *   <li>{@link #verify}: used at login. Accepts a TOTP code (never the same step twice) or an
 *       unused recovery code, which is then burned.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MfaService {

    public static final int RECOVERY_CODES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final JdbcTemplate jdbc;
    private final MfaCrypto crypto;
    private final ObjectMapper json;
    private final AppSecurityProperties props;
    private final Clock clock;

    public record MfaStatus(boolean enabled, Instant enabledAt, int recoveryCodesLeft, boolean enforced) {
    }

    public record MfaSetup(String secret, String otpauthUri) {
    }

    private record Row(String secret, boolean enabled, Instant enabledAt, long lastUsedStep, List<String> recovery) {
    }

    public MfaStatus status(UUID userId) {
        return find(userId)
                .map(r -> new MfaStatus(r.enabled, r.enabledAt, r.enabled ? r.recovery.size() : 0,
                        props.mfa().enforceForStaff()))
                .orElse(new MfaStatus(false, null, 0, props.mfa().enforceForStaff()));
    }

    public boolean isEnabled(UUID userId) {
        return find(userId).map(Row::enabled).orElse(false);
    }

    @Transactional
    public MfaSetup beginSetup(UUID userId, String accountEmail) {
        if (isEnabled(userId)) {
            throw new BusinessException(ErrorCode.MFA_ALREADY_ENABLED);
        }
        byte[] secret = Totp.newSecret();
        jdbc.update("""
                insert into user_mfa (user_id, secret_ciphertext, enabled, last_used_step, recovery_codes)
                values (?, ?, false, 0, '[]'::jsonb)
                on conflict (user_id) do update set secret_ciphertext = excluded.secret_ciphertext, enabled = false,
                    last_used_step = 0, recovery_codes = '[]'::jsonb, updated_at = now()
                """, userId, crypto.encrypt(secret));
        return new MfaSetup(Totp.base32(secret), Totp.otpauthUri(props.mfa().issuer(), accountEmail, secret));
    }

    /** Verifies the first code from the app and turns 2FA on. Returns the plaintext recovery codes (shown once). */
    @Transactional
    public List<String> confirmSetup(UUID userId, String code) {
        Row row = find(userId).orElseThrow(() -> new BusinessException(ErrorCode.MFA_NOT_ENABLED,
                "Start the setup first"));
        if (row.enabled) {
            throw new BusinessException(ErrorCode.MFA_ALREADY_ENABLED);
        }
        OptionalLong step = Totp.verify(crypto.decrypt(row.secret), code, clock.instant(), row.lastUsedStep);
        if (step.isEmpty()) {
            throw new BusinessException(ErrorCode.MFA_INVALID_CODE);
        }
        List<String> codes = newRecoveryCodes();
        jdbc.update("""
                update user_mfa set enabled = true, enabled_at = now(), last_used_step = ?, recovery_codes = ?::jsonb,
                       updated_at = now()
                where user_id = ?
                """, step.getAsLong(), hashesJson(codes), userId);
        AuditContext.force();
        AuditContext.action("mfa.enable");
        AuditContext.entity("USER", userId);
        log.info("2FA enabled for user {}", userId);
        return codes;
    }

    /**
     * Checks a login code (TOTP or recovery). On success the TOTP step is recorded (no replay),
     * or the recovery code is consumed.
     */
    @Transactional
    public boolean verify(UUID userId, String code) {
        Row row = find(userId).filter(Row::enabled).orElse(null);
        if (row == null || code == null) {
            return false;
        }
        String normalized = code.replaceAll("[\\s-]", "").toUpperCase();
        if (normalized.matches("\\d{" + Totp.DIGITS + "}")) {
            OptionalLong step = Totp.verify(crypto.decrypt(row.secret), normalized, clock.instant(), row.lastUsedStep);
            if (step.isEmpty()) {
                return false;
            }
            // Conditional update: two concurrent logins cannot both use the same step.
            return jdbc.update("update user_mfa set last_used_step = ?, updated_at = now() "
                    + "where user_id = ? and last_used_step < ?", step.getAsLong(), userId, step.getAsLong()) == 1;
        }
        String hash = Hashing.sha256Hex(normalized);
        if (!row.recovery.contains(hash)) {
            return false;
        }
        List<String> remaining = new ArrayList<>(row.recovery);
        remaining.remove(hash);
        jdbc.update("update user_mfa set recovery_codes = ?::jsonb, updated_at = now() where user_id = ?",
                toJson(remaining), userId);
        log.warn("User {} signed in with a recovery code ({} left)", userId, remaining.size());
        return true;
    }

    @Transactional
    public List<String> regenerateRecoveryCodes(UUID userId, String code) {
        if (!verify(userId, code)) {
            throw new BusinessException(ErrorCode.MFA_INVALID_CODE);
        }
        List<String> codes = newRecoveryCodes();
        jdbc.update("update user_mfa set recovery_codes = ?::jsonb, updated_at = now() where user_id = ?",
                hashesJson(codes), userId);
        AuditContext.force();
        AuditContext.action("mfa.recovery-codes.regenerate");
        AuditContext.entity("USER", userId);
        return codes;
    }

    /** The user turns 2FA off, proving possession with a current code. */
    @Transactional
    public void disable(UUID userId, String code) {
        if (!isEnabled(userId)) {
            throw new BusinessException(ErrorCode.MFA_NOT_ENABLED);
        }
        if (!verify(userId, code)) {
            throw new BusinessException(ErrorCode.MFA_INVALID_CODE);
        }
        jdbc.update("delete from user_mfa where user_id = ?", userId);
        AuditContext.force();
        AuditContext.action("mfa.disable");
        AuditContext.entity("USER", userId);
        log.info("2FA disabled by user {}", userId);
    }

    /** An administrator removes a user's 2FA (lost phone). The user must enrol again. */
    @Transactional
    public void adminReset(UUID userId) {
        jdbc.update("delete from user_mfa where user_id = ?", userId);
        AuditContext.action("mfa.reset");
        AuditContext.entity("USER", userId);
        log.warn("2FA reset for user {} by an administrator", userId);
    }

    // ------------------------------------------------------------------ helpers

    private Optional<Row> find(UUID userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select secret_ciphertext, enabled, enabled_at, last_used_step, recovery_codes::text as recovery
                from user_mfa where user_id = ?
                """, userId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> r = rows.getFirst();
        java.sql.Timestamp enabledAt = (java.sql.Timestamp) r.get("enabled_at");
        return Optional.of(new Row((String) r.get("secret_ciphertext"), Boolean.TRUE.equals(r.get("enabled")),
                enabledAt == null ? null : enabledAt.toInstant(), ((Number) r.get("last_used_step")).longValue(),
                readList((String) r.get("recovery"))));
    }

    private static List<String> newRecoveryCodes() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 10; j++) {
                if (j == 5) {
                    sb.append('-');
                }
                sb.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
            }
            codes.add(sb.toString());
        }
        return codes;
    }

    private String hashesJson(List<String> codes) {
        return toJson(codes.stream().map(c -> Hashing.sha256Hex(c.replace("-", ""))).toList());
    }

    private String toJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> readList(String s) {
        try {
            return s == null ? List.of() : json.readValue(s, new TypeReference<List<String>>() {
            });
        } catch (IOException e) {
            return List.of();
        }
    }
}
