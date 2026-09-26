package com.examprep.security.jwt;

import com.examprep.common.exception.ErrorCode;
import com.examprep.security.AppSecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Issues and verifies HS256-signed JWTs.
 *
 * <p>Access tokens are short-lived (default 15 min) and carry everything the API needs
 * to authorise a request (user id, roles, session, generation), so there is no DB hit
 * per request. Refresh tokens are long-lived, single-use (rotated on every refresh) and
 * tracked in Redis by {@code TokenStore}.
 */
@Service
public class JwtService {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_SESSION = "sid";
    private static final String CLAIM_GENERATION = "gen";
    private static final String CLAIM_MFA = "mfa";
    private static final Pattern ROLE_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,31}$");
    /** Lifetime of the 2FA challenge token issued after a correct password. */
    public static final Duration MFA_CHALLENGE_TTL = Duration.ofMinutes(5);

    private final AppSecurityProperties.Jwt props;
    private final Clock clock;
    private final SecretKey key;
    private final JwtParser parser;

    public JwtService(AppSecurityProperties securityProperties, Clock clock) {
        this.props = securityProperties.jwt();
        this.clock = clock;
        byte[] keyBytes = Decoders.BASE64.decode(props.secret());
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.security.jwt.secret must be at least 256 bits (32 bytes) base64");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.parser = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(props.issuer())
                .clockSkewSeconds(30)
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    public IssuedToken issueAccessToken(UUID userId, String email, Collection<String> roles, String sessionId,
                                        long generation, boolean mfaVerified) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(props.accessTokenTtl());
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .id(jti)
                .issuer(props.issuer())
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_TYPE, TokenType.ACCESS.name())
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLES, roles.stream().sorted().toList())
                .claim(CLAIM_SESSION, sessionId)
                .claim(CLAIM_GENERATION, generation)
                .claim(CLAIM_MFA, mfaVerified)
                .signWith(key)
                .compact();
        return new IssuedToken(token, jti, expiresAt);
    }

    /**
     * A 5-minute token proving "password was correct, 2FA code still needed". It carries no
     * roles and is only accepted by {@code POST /auth/login/mfa}; the jti is single-use in Redis.
     */
    public IssuedToken issueMfaChallengeToken(UUID userId) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(MFA_CHALLENGE_TTL);
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .id(jti)
                .issuer(props.issuer())
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_TYPE, TokenType.MFA.name())
                .signWith(key)
                .compact();
        return new IssuedToken(token, jti, expiresAt);
    }

    public IssuedToken issueRefreshToken(UUID userId, String sessionId, long generation) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(props.refreshTokenTtl());
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .id(jti)
                .issuer(props.issuer())
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_TYPE, TokenType.REFRESH.name())
                .claim(CLAIM_SESSION, sessionId)
                .claim(CLAIM_GENERATION, generation)
                .signWith(key)
                .compact();
        return new IssuedToken(token, jti, expiresAt);
    }

    /**
     * Verifies signature, issuer, expiry and token type.
     *
     * @throws InvalidTokenException TOKEN_EXPIRED or TOKEN_INVALID
     */
    public TokenClaims parse(String token, TokenType expectedType) {
        Claims claims;
        try {
            claims = parser.parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException(ErrorCode.TOKEN_INVALID);
        }

        TokenType type;
        try {
            type = TokenType.valueOf(claims.get(CLAIM_TYPE, String.class));
        } catch (RuntimeException e) {
            throw new InvalidTokenException(ErrorCode.TOKEN_INVALID);
        }
        if (type != expectedType) {
            throw new InvalidTokenException(ErrorCode.TOKEN_INVALID, "Wrong token type");
        }

        Number generation = claims.get(CLAIM_GENERATION, Number.class);
        return new TokenClaims(
                claims.getId(),
                UUID.fromString(claims.getSubject()),
                claims.get(CLAIM_EMAIL, String.class),
                parseRoles(claims.get(CLAIM_ROLES, List.class)),
                claims.get(CLAIM_SESSION, String.class),
                generation == null ? 0L : generation.longValue(),
                Boolean.TRUE.equals(claims.get(CLAIM_MFA, Boolean.class)),
                type,
                claims.getIssuedAt().toInstant(),
                claims.getExpiration().toInstant());
    }

    private static Set<String> parseRoles(List<?> raw) {
        if (raw == null) {
            return Set.of();
        }
        // Signed by us, but still reject anything that is not a well-formed role code.
        return raw.stream().map(Object::toString).filter(r -> ROLE_CODE.matcher(r).matches())
                .collect(Collectors.toUnmodifiableSet());
    }
}
