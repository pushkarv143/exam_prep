package com.examprep.security.session;

import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.security.TokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Login sessions (one per device), keyed by the JWT {@code sid}. The row gives users and
 * support a device list, and it remembers whether the session passed 2FA. Revocation has two
 * parts: the DB row is marked revoked, and a Redis flag makes the access-token filter and the
 * refresh flow reject the session within a request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final JdbcTemplate jdbc;
    private final TokenStore tokenStore;

    public record SessionDto(String id, Instant createdAt, Instant lastSeenAt, Instant expiresAt, String ip,
                             String userAgent, String device, boolean mfaVerified, boolean current) {
    }

    public void open(UUID userId, String sid, String ip, String userAgent, boolean mfaVerified, Instant expiresAt) {
        jdbc.update("""
                insert into user_sessions (id, user_id, ip, user_agent, mfa_verified, expires_at)
                values (?, ?, ?, ?, ?, ?)
                on conflict (id) do update set last_seen_at = now(), expires_at = excluded.expires_at,
                    mfa_verified = user_sessions.mfa_verified or excluded.mfa_verified
                """, sid, userId, cut(ip, 64), cut(userAgent, 512), mfaVerified, Timestamp.from(expiresAt));
    }

    /**
     * Called on refresh. Returns whether the session passed 2FA, or empty if the session was revoked.
     * Sessions created before this table existed are adopted on their first refresh.
     */
    public Optional<Boolean> touch(UUID userId, String sid, String ip, String userAgent, Instant expiresAt) {
        List<Object[]> rows = jdbc.query("select revoked_at is not null, mfa_verified from user_sessions where id = ?",
                (rs, i) -> new Object[]{rs.getBoolean(1), rs.getBoolean(2)}, sid);
        if (rows.isEmpty()) {
            open(userId, sid, ip, userAgent, false, expiresAt);
            return Optional.of(false);
        }
        if ((Boolean) rows.getFirst()[0]) {
            return Optional.empty();
        }
        jdbc.update("update user_sessions set last_seen_at = now(), expires_at = ?, ip = coalesce(?, ip) where id = ?",
                Timestamp.from(expiresAt), cut(ip, 64), sid);
        return Optional.of((Boolean) rows.getFirst()[1]);
    }

    public boolean isMfaVerified(String sid) {
        List<Boolean> v = jdbc.queryForList("select mfa_verified from user_sessions where id = ? and revoked_at is null",
                Boolean.class, sid);
        return !v.isEmpty() && Boolean.TRUE.equals(v.getFirst());
    }

    public void markMfaVerified(String sid) {
        jdbc.update("update user_sessions set mfa_verified = true where id = ?", sid);
    }

    public List<SessionDto> active(UUID userId, String currentSid) {
        return jdbc.query("""
                select id, created_at, last_seen_at, expires_at, ip, user_agent, mfa_verified from user_sessions
                where user_id = ? and revoked_at is null and expires_at > now()
                order by last_seen_at desc limit 50
                """, (rs, i) -> new SessionDto(rs.getString("id"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("last_seen_at").toInstant(), rs.getTimestamp("expires_at").toInstant(),
                rs.getString("ip"), rs.getString("user_agent"), describe(rs.getString("user_agent")),
                rs.getBoolean("mfa_verified"), rs.getString("id").equals(currentSid)), userId);
    }

    public void revoke(UUID userId, String sid, String reason) {
        int n = jdbc.update("update user_sessions set revoked_at = now(), revoke_reason = ? "
                + "where id = ? and user_id = ? and revoked_at is null", reason, sid, userId);
        if (n == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Session not found or already ended");
        }
        tokenStore.revokeSession(sid);
    }

    /**
     * Logs the user out everywhere. With {@code keepSid}, that session survives: its generation is
     * not bumped, so only the other sessions are revoked, one by one.
     */
    public int revokeAll(UUID userId, String keepSid, String reason) {
        List<String> sids = jdbc.queryForList("select id from user_sessions where user_id = ? and revoked_at is null "
                + "and (?::varchar is null or id <> ?)", String.class, userId, keepSid, keepSid);
        jdbc.update("update user_sessions set revoked_at = now(), revoke_reason = ? where user_id = ? "
                + "and revoked_at is null and (?::varchar is null or id <> ?)", reason, userId, keepSid, keepSid);
        if (keepSid == null) {
            tokenStore.revokeAllSessions(userId);   // also kills sessions that have no row yet
        } else {
            sids.forEach(tokenStore::revokeSession);
        }
        log.info("Revoked {} sessions of user {} ({})", sids.size(), userId, reason);
        return sids.size();
    }

    public void end(String sid) {
        jdbc.update("update user_sessions set revoked_at = now(), revoke_reason = 'logout' "
                + "where id = ? and revoked_at is null", sid);
    }

    @Scheduled(cron = "0 23 4 * * *")
    public void purgeOld() {
        jdbc.update("delete from user_sessions where expires_at < now() - interval '30 days'");
    }

    /** "Chrome on Windows"-style label from a user agent. */
    static String describe(String ua) {
        if (ua == null || ua.isBlank()) {
            return "Unknown device";
        }
        String browser = ua.contains("Edg/") ? "Edge" : ua.contains("OPR/") ? "Opera" : ua.contains("Chrome/") ? "Chrome"
                : ua.contains("Firefox/") ? "Firefox" : ua.contains("Safari/") ? "Safari"
                : ua.contains("okhttp") || ua.contains("Dart") ? "App" : "Browser";
        String os = ua.contains("Android") ? "Android" : ua.contains("iPhone") || ua.contains("iPad") ? "iOS"
                : ua.contains("Windows") ? "Windows" : ua.contains("Mac OS") ? "macOS"
                : ua.contains("Linux") ? "Linux" : "an unknown OS";
        return browser + " on " + os;
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
