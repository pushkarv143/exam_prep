package com.examprep.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-backed server-side token state. All keys expire on their own, so nothing
 * needs to be cleaned up.
 *
 * <pre>
 * auth:rt:{jti}           -> "{userId}|{sessionId}"   live refresh tokens (single use, TTL = refresh TTL)
 * auth:bl:{jti}           -> "1"                      blacklisted access tokens (TTL = remaining lifetime)
 * auth:user:{userId}      -> hash {gen, sid}          token generation + active session (single-session mode)
 * auth:pwreset:{sha256}   -> "{userId}"               password-reset tokens (only the hash is stored)
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class TokenStore {

    private static final String REFRESH = "auth:rt:";
    private static final String BLACKLIST = "auth:bl:";
    private static final String USER = "auth:user:";
    private static final String PASSWORD_RESET = "auth:pwreset:";
    private static final String FIELD_GENERATION = "gen";
    private static final String FIELD_SESSION = "sid";

    /**
     * GET + DEL in one atomic step. Same result as GETDEL, but a Lua script also runs on Redis
     * older than 6.2 (for example the Windows ports some developers have installed).
     */
    private static final RedisScript<String> GET_AND_DELETE = RedisScript.of(
            "local v = redis.call('GET', KEYS[1]) if v then redis.call('DEL', KEYS[1]) end return v", String.class);

    private final StringRedisTemplate redis;
    private final AppSecurityProperties props;

    // ---------------------------------------------------------------- refresh tokens

    public void saveRefreshToken(String jti, UUID userId, String sessionId) {
        redis.opsForValue().set(REFRESH + jti, userId + "|" + sessionId, props.jwt().refreshTokenTtl());
    }

    /** Atomically fetches and deletes a refresh token, so each one can be used only once. */
    public Optional<RefreshTokenRecord> consumeRefreshToken(String jti) {
        String value = getAndDelete(REFRESH + jti);
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split("\\|", 2);
        return Optional.of(new RefreshTokenRecord(UUID.fromString(parts[0]), parts[1]));
    }

    public void deleteRefreshToken(String jti) {
        redis.delete(REFRESH + jti);
    }

    // ---------------------------------------------------------------- access tokens

    public void blacklistAccessToken(String jti, Duration remainingLifetime) {
        if (!remainingLifetime.isNegative() && !remainingLifetime.isZero()) {
            redis.opsForValue().set(BLACKLIST + jti, "1", remainingLifetime);
        }
    }

    /**
     * Loads everything the JWT filter needs in ONE pipelined round trip:
     * the blacklist flag for the access token, plus the user's generation and active session.
     */
    public TokenState loadState(String accessJti, UUID userId) {
        List<Object> results = redis.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings("unchecked")
            public <K, V> Object execute(RedisOperations<K, V> operations) {
                RedisOperations<String, String> ops = (RedisOperations<String, String>) operations;
                ops.hasKey(BLACKLIST + accessJti);
                ops.opsForHash().multiGet(USER + userId, List.of(FIELD_GENERATION, FIELD_SESSION));
                return null;
            }
        });
        boolean blacklisted = Boolean.TRUE.equals(results.get(0));
        List<?> fields = (List<?>) results.get(1);
        return new TokenState(blacklisted, parseLong(fields.get(0)), (String) fields.get(1));
    }

    // ---------------------------------------------------------------- per-user state

    public long currentGeneration(UUID userId) {
        return parseLong(redis.opsForHash().get(USER + userId, FIELD_GENERATION));
    }

    /** Revokes every access and refresh token of the user issued before now. */
    public void revokeAllSessions(UUID userId) {
        String key = USER + userId;
        redis.opsForHash().increment(key, FIELD_GENERATION, 1);
        redis.opsForHash().delete(key, FIELD_SESSION);
        redis.expire(key, props.jwt().refreshTokenTtl());
    }

    public void setActiveSession(UUID userId, String sessionId) {
        String key = USER + userId;
        redis.opsForHash().put(key, FIELD_SESSION, sessionId);
        redis.expire(key, props.jwt().refreshTokenTtl());
    }

    public Optional<String> activeSession(UUID userId) {
        return Optional.ofNullable((String) redis.opsForHash().get(USER + userId, FIELD_SESSION));
    }

    public void clearActiveSession(UUID userId, String sessionId) {
        activeSession(userId)
                .filter(sessionId::equals)
                .ifPresent(sid -> redis.opsForHash().delete(USER + userId, FIELD_SESSION));
    }

    // ---------------------------------------------------------------- password reset

    public void savePasswordResetToken(String tokenHash, UUID userId) {
        redis.opsForValue().set(PASSWORD_RESET + tokenHash, userId.toString(), props.passwordResetTtl());
    }

    public Optional<UUID> consumePasswordResetToken(String tokenHash) {
        return Optional.ofNullable(getAndDelete(PASSWORD_RESET + tokenHash))
                .map(UUID::fromString);
    }

    private String getAndDelete(String key) {
        return redis.execute(GET_AND_DELETE, List.of(key));
    }

    private static long parseLong(Object value) {
        return value == null ? 0L : Long.parseLong(value.toString());
    }

    public record RefreshTokenRecord(UUID userId, String sessionId) {
    }

    public record TokenState(boolean blacklisted, long generation, String activeSessionId) {
    }
}
