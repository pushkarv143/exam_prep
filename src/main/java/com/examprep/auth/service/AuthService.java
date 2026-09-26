package com.examprep.auth.service;

import com.examprep.audit.service.AuditRecord;
import com.examprep.audit.service.AuditService;
import com.examprep.auth.dto.AuthResponse;
import com.examprep.auth.dto.ClientInfo;
import com.examprep.auth.dto.ForgotPasswordRequest;
import com.examprep.auth.dto.LoginRequest;
import com.examprep.auth.dto.MfaLoginRequest;
import com.examprep.auth.dto.RegisterRequest;
import com.examprep.auth.dto.ResetPasswordRequest;
import com.examprep.auth.event.PasswordResetRequestedEvent;
import com.examprep.auth.event.UserRegisteredEvent;
import com.examprep.common.config.AppProperties;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.util.Hashing;
import com.examprep.security.AppSecurityProperties;
import com.examprep.security.AuthUser;
import com.examprep.security.TokenStore;
import com.examprep.security.jwt.InvalidTokenException;
import com.examprep.security.jwt.IssuedToken;
import com.examprep.security.jwt.JwtService;
import com.examprep.security.jwt.TokenClaims;
import com.examprep.security.jwt.TokenType;
import com.examprep.security.mfa.MfaService;
import com.examprep.security.session.SessionService;
import com.examprep.user.entity.Role;
import com.examprep.user.entity.Roles;
import com.examprep.user.entity.User;
import com.examprep.user.entity.UserStatus;
import com.examprep.user.mapper.UserMapper;
import com.examprep.user.repository.RoleRepository;
import com.examprep.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Authentication flows: register, login (with optional TOTP second step), refresh (with
 * rotation), logout, forgot/reset password.
 *
 * <p>Security properties:
 * <ul>
 *   <li>Unknown user and wrong password give the same error, and a dummy bcrypt check
 *       evens out response time, so accounts cannot be enumerated.</li>
 *   <li>Refresh tokens are single-use (atomic GET+DEL). A replayed token is rejected.</li>
 *   <li>With 2FA, a correct password only yields a 5-minute single-use challenge. Five wrong
 *       codes destroy it. The session remembers that it passed 2FA, so refreshes keep the
 *       {@code mfa} claim.</li>
 *   <li>Password reset tokens are random 256-bit values stored only as SHA-256 hashes
 *       with a TTL. A successful reset revokes every existing session.</li>
 *   <li>Staff sign-ins (and failed attempts on staff accounts) are written to the audit log.</li>
 * </ul>
 */
@Slf4j
@Service
public class AuthService {

    private static final Pattern PHONE = Pattern.compile("^[6-9]\\d{9}$");
    private static final int MAX_MFA_FAILURES = 5;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenStore tokenStore;
    private final SessionService sessions;
    private final MfaService mfa;
    private final AuditService audit;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher events;
    private final AppSecurityProperties securityProperties;
    private final AppProperties appProperties;
    private final Clock clock;
    /** Hash used to burn equal CPU time when the user does not exist. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService, TokenStore tokenStore,
                       SessionService sessions, MfaService mfa, AuditService audit, UserMapper userMapper,
                       ApplicationEventPublisher events, AppSecurityProperties securityProperties,
                       AppProperties appProperties, Clock clock) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
        this.sessions = sessions;
        this.mfa = mfa;
        this.audit = audit;
        this.userMapper = userMapper;
        this.events = events;
        this.securityProperties = securityProperties;
        this.appProperties = appProperties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, ClientInfo client) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailNormalized(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "An account with this email already exists");
        }
        if (request.phone() != null && userRepository.existsByPhone(request.phone())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "An account with this phone already exists");
        }

        Role studentRole = roleRepository.findByName(Roles.STUDENT)
                .orElseThrow(() -> new IllegalStateException("STUDENT role missing - check Flyway seed"));

        User user = new User();
        user.setEmail(email);
        user.setPhone(request.phone());
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setTargetExamCode(request.targetExamCode());
        user.setStatus(UserStatus.ACTIVE);
        user.getRoles().add(studentRole);
        user.setLastLoginAt(Instant.now(clock));
        userRepository.saveAndFlush(user);   // flush so a unique-index race surfaces here

        events.publishEvent(new UserRegisteredEvent(user.getId(), user.getEmail(), user.getFullName()));
        log.info("Registered student {}", user.getId());
        return issueTokens(user, newSessionId(), false, client);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, ClientInfo client) {
        Optional<User> found = findByIdentifier(request.identifier());
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        User user = found.get();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditStaffLogin(user, "auth.login", AuditRecord.Outcome.FAILURE, "INVALID_CREDENTIALS", client);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!user.isActive()) {
            auditStaffLogin(user, "auth.login", AuditRecord.Outcome.DENIED, "ACCOUNT_DISABLED", client);
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (mfa.isEnabled(user.getId())) {
            IssuedToken challenge = jwtService.issueMfaChallengeToken(user.getId());
            tokenStore.saveMfaChallenge(challenge.tokenId(), user.getId(), JwtService.MFA_CHALLENGE_TTL);
            return AuthResponse.mfaChallenge(challenge.token());
        }
        user.setLastLoginAt(Instant.now(clock));
        auditStaffLogin(user, "auth.login", AuditRecord.Outcome.SUCCESS, null, client);
        return issueTokens(user, newSessionId(), false, client);
    }

    /** Second step: exchanges a live challenge and a valid TOTP or recovery code for tokens. */
    @Transactional
    public AuthResponse loginMfa(MfaLoginRequest request, ClientInfo client) {
        TokenClaims challenge;
        try {
            challenge = jwtService.parse(request.mfaToken(), TokenType.MFA);
        } catch (InvalidTokenException e) {
            throw new BusinessException(ErrorCode.MFA_CHALLENGE_EXPIRED);
        }
        UUID userId = tokenStore.peekMfaChallenge(challenge.tokenId())
                .filter(id -> id.equals(challenge.userId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.MFA_CHALLENGE_EXPIRED));
        User user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MFA_CHALLENGE_EXPIRED));
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (!mfa.verify(userId, request.code())) {
            long failures = tokenStore.recordMfaFailure(challenge.tokenId(), MAX_MFA_FAILURES,
                    JwtService.MFA_CHALLENGE_TTL);
            auditStaffLogin(user, "auth.login.mfa", AuditRecord.Outcome.FAILURE, "MFA_INVALID_CODE", client);
            if (failures >= MAX_MFA_FAILURES) {
                throw new BusinessException(ErrorCode.MFA_CHALLENGE_EXPIRED,
                        "Too many wrong codes. Please log in again.");
            }
            throw new BusinessException(ErrorCode.MFA_INVALID_CODE);
        }
        if (!tokenStore.consumeMfaChallenge(challenge.tokenId())) {
            throw new BusinessException(ErrorCode.MFA_CHALLENGE_EXPIRED);   // used concurrently
        }
        user.setLastLoginAt(Instant.now(clock));
        auditStaffLogin(user, "auth.login", AuditRecord.Outcome.SUCCESS, null, client);
        return issueTokens(user, newSessionId(), true, client);
    }

    /**
     * Exchanges a valid refresh token for a new access and refresh pair in the same
     * session. The presented refresh token is consumed, so a second use fails.
     */
    @Transactional
    public AuthResponse refresh(String refreshToken, ClientInfo client) {
        TokenClaims claims = jwtService.parse(refreshToken, TokenType.REFRESH);
        TokenStore.RefreshTokenRecord stored = tokenStore.consumeRefreshToken(claims.tokenId())
                .orElseThrow(() -> new InvalidTokenException(ErrorCode.TOKEN_INVALID,
                        "Refresh token has already been used or revoked"));

        if (!stored.userId().equals(claims.userId())
                || claims.generation() < tokenStore.currentGeneration(claims.userId())
                || tokenStore.isSessionRevoked(claims.sessionId())) {
            throw new InvalidTokenException(ErrorCode.SESSION_REVOKED);
        }

        User user = userRepository.findWithRolesById(claims.userId())
                .orElseThrow(() -> new InvalidTokenException(ErrorCode.TOKEN_INVALID));
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (securityProperties.singleSessionEnabled() && user.hasRole(Roles.STUDENT)) {
            String active = tokenStore.activeSession(user.getId()).orElse(null);
            if (active != null && !active.equals(claims.sessionId())) {
                throw new InvalidTokenException(ErrorCode.SESSION_REVOKED,
                        "You have logged in on another device. This session has ended.");
            }
        }
        Instant refreshExpiry = Instant.now(clock).plus(securityProperties.jwt().refreshTokenTtl());
        boolean mfaVerified = sessions.touch(user.getId(), claims.sessionId(), client.ip(), client.userAgent(),
                        refreshExpiry)
                .orElseThrow(() -> new InvalidTokenException(ErrorCode.SESSION_REVOKED));
        return issueTokens(user, claims.sessionId(), mfaVerified, null);
    }

    /** Revokes the current access token and, if supplied, the refresh token of the same session. */
    public void logout(AuthUser principal, String refreshToken) {
        tokenStore.blacklistAccessToken(principal.tokenId(),
                Duration.between(Instant.now(clock), principal.tokenExpiresAt()));
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                TokenClaims claims = jwtService.parse(refreshToken, TokenType.REFRESH);
                if (claims.userId().equals(principal.id())) {
                    tokenStore.deleteRefreshToken(claims.tokenId());
                }
            } catch (InvalidTokenException ignored) {
                // Already expired or invalid, so there is nothing left to revoke.
            }
        }
        tokenStore.clearActiveSession(principal.id(), principal.sessionId());
        sessions.end(principal.sessionId());
    }

    /**
     * Always succeeds from the caller's point of view, whether or not the email exists,
     * so accounts cannot be enumerated. A reset email is sent only when an active user matches.
     * Read-write: the email is written to the outbox in this transaction.
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmailNormalized(normalizeEmail(request.email()))
                .filter(User::isActive)
                .ifPresent(user -> {
                    String token = Hashing.randomUrlToken(32);
                    tokenStore.savePasswordResetToken(Hashing.sha256Hex(token), user.getId());
                    String resetUrl = appProperties.frontendUrl() + "/reset-password?token=" + token;
                    events.publishEvent(new PasswordResetRequestedEvent(user.getId(), user.getEmail(),
                            user.getFullName(), resetUrl, securityProperties.passwordResetTtl().toMinutes()));
                    log.info("Password reset requested for user {}", user.getId());
                });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        UUID userId = tokenStore.consumePasswordResetToken(Hashing.sha256Hex(request.token()))
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                        "Reset link is invalid or has expired"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "Reset link is invalid"));
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        sessions.revokeAll(userId, null, "password reset");
        log.info("Password reset completed for user {}", userId);
    }

    // ------------------------------------------------------------------------ helpers

    private AuthResponse issueTokens(User user, String sessionId, boolean mfaVerified, ClientInfo newSession) {
        long generation = tokenStore.currentGeneration(user.getId());
        IssuedToken access = jwtService.issueAccessToken(user.getId(), user.getEmail(), user.roleNames(),
                sessionId, generation, mfaVerified);
        IssuedToken refresh = jwtService.issueRefreshToken(user.getId(), sessionId, generation);
        tokenStore.saveRefreshToken(refresh.tokenId(), user.getId(), sessionId);
        if (newSession != null) {
            sessions.open(user.getId(), sessionId, newSession.ip(), newSession.userAgent(), mfaVerified,
                    refresh.expiresAt());
        }
        if (securityProperties.singleSessionEnabled() && user.hasRole(Roles.STUDENT)) {
            tokenStore.setActiveSession(user.getId(), sessionId);
        }
        return new AuthResponse(access.token(), refresh.token(), "Bearer", access.expiresAt(),
                refresh.expiresAt(), userMapper.toDto(user), null, null);
    }

    private void auditStaffLogin(User user, String action, AuditRecord.Outcome outcome, String errorCode,
                                 ClientInfo client) {
        if (!user.isStaff()) {
            return;   // student logins are high-volume and not admin activity
        }
        audit.record(AuditRecord.builder()
                .actorId(user.getId())
                .actorEmail(user.getEmail())
                .actorRoles(user.roleNames())
                .action(action)
                .entityType("USER")
                .entityId(user.getId().toString())
                .outcome(outcome)
                .httpMethod("POST")
                .path(action.equals("auth.login.mfa") ? "/api/v1/auth/login/mfa" : "/api/v1/auth/login")
                .errorCode(errorCode)
                .metadata(audit.snapshot(Map.of("mfaEnabled", mfa.isEnabled(user.getId()))))
                .ip(client == null ? null : client.ip())
                .userAgent(client == null ? null : client.userAgent())
                .requestId(MDC.get("requestId"))
                .build());
    }

    private Optional<User> findByIdentifier(String identifier) {
        String value = identifier.trim();
        if (PHONE.matcher(value).matches()) {
            return userRepository.findByPhone(value);
        }
        return userRepository.findByEmailNormalized(normalizeEmail(value));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private static String newSessionId() {
        return UUID.randomUUID().toString();
    }
}
