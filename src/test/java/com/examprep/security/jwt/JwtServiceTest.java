package com.examprep.security.jwt;

import com.examprep.common.exception.ErrorCode;
import com.examprep.security.AppSecurityProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("unit-test-secret-that-is-at-least-32-bytes!!".getBytes());
    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    private final JwtService jwt = service(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void access_token_round_trip_preserves_claims() {
        UUID userId = UUID.randomUUID();
        IssuedToken issued = jwt.issueAccessToken(userId, "a@b.com", Set.of("STUDENT", "TEACHER"),
                "sid-1", 3, true);

        TokenClaims claims = jwt.parse(issued.token(), TokenType.ACCESS);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.email()).isEqualTo("a@b.com");
        assertThat(claims.roles()).containsExactlyInAnyOrder("STUDENT", "TEACHER");
        assertThat(claims.mfaVerified()).isTrue();
        assertThat(claims.sessionId()).isEqualTo("sid-1");
        assertThat(claims.generation()).isEqualTo(3);
        assertThat(claims.tokenId()).isEqualTo(issued.tokenId());
        assertThat(claims.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void refresh_token_cannot_be_used_as_access_token() {
        IssuedToken refresh = jwt.issueRefreshToken(UUID.randomUUID(), "sid", 0);

        assertThatThrownBy(() -> jwt.parse(refresh.token(), TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    void expired_token_is_reported_as_expired() {
        IssuedToken issued = jwt.issueAccessToken(UUID.randomUUID(), "a@b.com", List.of("STUDENT"), "s", 0, false);
        JwtService later = service(SECRET, Clock.fixed(NOW.plus(Duration.ofMinutes(20)), ZoneOffset.UTC));

        assertThatThrownBy(() -> later.parse(issued.token(), TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void token_signed_with_other_key_is_rejected() {
        String otherSecret = Base64.getEncoder()
                .encodeToString("a-completely-different-secret-key-32-bytes".getBytes());
        IssuedToken foreign = service(otherSecret, Clock.fixed(NOW, ZoneOffset.UTC))
                .issueAccessToken(UUID.randomUUID(), "x@y.com", List.of("SUPER_ADMIN"), "s", 0, false);

        assertThatThrownBy(() -> jwt.parse(foreign.token(), TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    void mfa_challenge_token_is_not_an_access_or_refresh_token() {
        IssuedToken challenge = jwt.issueMfaChallengeToken(UUID.randomUUID());

        assertThat(jwt.parse(challenge.token(), TokenType.MFA).roles()).isEmpty();
        assertThatThrownBy(() -> jwt.parse(challenge.token(), TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> jwt.parse(challenge.token(), TokenType.REFRESH))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void access_token_without_mfa_claim_is_not_mfa_verified() {
        IssuedToken issued = jwt.issueAccessToken(UUID.randomUUID(), "a@b.com", List.of("TEACHER"), "s", 0, false);
        assertThat(jwt.parse(issued.token(), TokenType.ACCESS).mfaVerified()).isFalse();
    }

    @Test
    void short_secret_fails_fast_at_startup() {
        String weak = Base64.getEncoder().encodeToString("too-short".getBytes());
        assertThatThrownBy(() -> service(weak, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }

    private static JwtService service(String secret, Clock clock) {
        AppSecurityProperties props = new AppSecurityProperties(
                new AppSecurityProperties.Jwt(secret, "examprep", Duration.ofMinutes(15), Duration.ofDays(7)),
                false, Duration.ofMinutes(30), new AppSecurityProperties.Cors(List.of("*")),
                new AppSecurityProperties.Mfa("x", false, "ExamPrep"), new AppSecurityProperties.AdminIpAllowlist(false));
        return new JwtService(props, clock);
    }
}
