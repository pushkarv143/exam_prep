package com.examprep.auth.service;

import com.examprep.auth.dto.AuthResponse;
import com.examprep.auth.dto.ForgotPasswordRequest;
import com.examprep.auth.dto.LoginRequest;
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
import com.examprep.user.entity.Role;
import com.examprep.user.entity.RoleName;
import com.examprep.user.entity.User;
import com.examprep.user.entity.UserStatus;
import com.examprep.user.mapper.UserMapper;
import com.examprep.user.repository.RoleRepository;
import com.examprep.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Authentication flows: register, login, refresh (with rotation), logout, forgot/reset password.
 *
 * <p>Security properties:
 * <ul>
 *   <li>Unknown user and wrong password give the same error, and a dummy bcrypt check
 *       evens out response time, so accounts cannot be enumerated.</li>
 *   <li>Refresh tokens are single-use (atomic GET+DEL). A replayed token is rejected.</li>
 *   <li>Password reset tokens are random 256-bit values stored only as SHA-256 hashes
 *       with a TTL. A successful reset revokes every existing session.</li>
 * </ul>
 */
@Slf4j
@Service
public class AuthService {

    private static final Pattern PHONE = Pattern.compile("^[6-9]\\d{9}$");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenStore tokenStore;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher events;
    private final AppSecurityProperties securityProperties;
    private final AppProperties appProperties;
    private final Clock clock;
    /** Hash used to burn equal CPU time when the user does not exist. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService, TokenStore tokenStore,
                       UserMapper userMapper, ApplicationEventPublisher events,
                       AppSecurityProperties securityProperties, AppProperties appProperties, Clock clock) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
        this.userMapper = userMapper;
        this.events = events;
        this.securityProperties = securityProperties;
        this.appProperties = appProperties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailNormalized(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "An account with this email already exists");
        }
        if (request.phone() != null && userRepository.existsByPhone(request.phone())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "An account with this phone already exists");
        }

        Role studentRole = roleRepository.findByName(RoleName.STUDENT)
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
        return issueTokens(user, newSessionId());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Optional<User> found = findByIdentifier(request.identifier());
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        User user = found.get();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        user.setLastLoginAt(Instant.now(clock));
        return issueTokens(user, newSessionId());
    }

    /**
     * Exchanges a valid refresh token for a new access and refresh pair in the same
     * session. The presented refresh token is consumed, so a second use fails.
     */
    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        TokenClaims claims = jwtService.parse(refreshToken, TokenType.REFRESH);
        TokenStore.RefreshTokenRecord stored = tokenStore.consumeRefreshToken(claims.tokenId())
                .orElseThrow(() -> new InvalidTokenException(ErrorCode.TOKEN_INVALID,
                        "Refresh token has already been used or revoked"));

        if (!stored.userId().equals(claims.userId())
                || claims.generation() < tokenStore.currentGeneration(claims.userId())) {
            throw new InvalidTokenException(ErrorCode.SESSION_REVOKED);
        }

        User user = userRepository.findWithRolesById(claims.userId())
                .orElseThrow(() -> new InvalidTokenException(ErrorCode.TOKEN_INVALID));
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (securityProperties.singleSessionEnabled() && user.hasRole(RoleName.STUDENT)) {
            String active = tokenStore.activeSession(user.getId()).orElse(null);
            if (active != null && !active.equals(claims.sessionId())) {
                throw new InvalidTokenException(ErrorCode.SESSION_REVOKED,
                        "You have logged in on another device. This session has ended.");
            }
        }
        return issueTokens(user, claims.sessionId());
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
    }

    /**
     * Always succeeds from the caller's point of view, whether or not the email exists,
     * so accounts cannot be enumerated. A reset email is sent only when an active user matches.
     */
    @Transactional(readOnly = true)
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
        tokenStore.revokeAllSessions(userId);
        log.info("Password reset completed for user {}", userId);
    }

    // ------------------------------------------------------------------------ helpers

    private AuthResponse issueTokens(User user, String sessionId) {
        long generation = tokenStore.currentGeneration(user.getId());
        IssuedToken access = jwtService.issueAccessToken(user.getId(), user.getEmail(), user.roleNames(),
                sessionId, generation);
        IssuedToken refresh = jwtService.issueRefreshToken(user.getId(), sessionId, generation);
        tokenStore.saveRefreshToken(refresh.tokenId(), user.getId(), sessionId);

        if (securityProperties.singleSessionEnabled() && user.hasRole(RoleName.STUDENT)) {
            tokenStore.setActiveSession(user.getId(), sessionId);
        }
        return new AuthResponse(access.token(), refresh.token(), "Bearer", access.expiresAt(),
                refresh.expiresAt(), userMapper.toDto(user));
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
