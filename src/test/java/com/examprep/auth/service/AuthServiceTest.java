package com.examprep.auth.service;

import com.examprep.auth.dto.ForgotPasswordRequest;
import com.examprep.auth.dto.LoginRequest;
import com.examprep.auth.dto.RegisterRequest;
import com.examprep.common.config.AppProperties;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.security.AppSecurityProperties;
import com.examprep.security.TokenStore;
import com.examprep.security.jwt.JwtService;
import com.examprep.user.entity.User;
import com.examprep.user.entity.UserStatus;
import com.examprep.user.mapper.UserMapper;
import com.examprep.user.repository.RoleRepository;
import com.examprep.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock TokenStore tokenStore;
    @Mock UserMapper userMapper;
    @Mock ApplicationEventPublisher events;

    AuthService service;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$dummy");
        AppSecurityProperties securityProps = new AppSecurityProperties(
                new AppSecurityProperties.Jwt("x", "examprep", Duration.ofMinutes(15), Duration.ofDays(7)),
                false, Duration.ofMinutes(30), new AppSecurityProperties.Cors(List.of()));
        AppProperties appProps = new AppProperties("http://localhost:5173",
                new AppProperties.RateLimit(true),
                new AppProperties.Notification(new AppProperties.Notification.Email(false, "x@y")));
        service = new AuthService(userRepository, roleRepository, passwordEncoder, jwtService, tokenStore,
                userMapper, events, securityProps, appProps, Clock.systemUTC());
    }

    @Test
    void login_unknown_user_still_runs_bcrypt_and_fails_generically() {
        when(userRepository.findByEmailNormalized("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("Ghost@Example.com", "whatever1")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        verify(passwordEncoder).matches(eq("whatever1"), anyString());   // timing equalisation
        verifyNoInteractions(jwtService);
    }

    @Test
    void login_by_phone_uses_phone_lookup() {
        when(userRepository.findByPhone("9876543210")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("9876543210", "pw123456")))
                .isInstanceOf(BusinessException.class);
        verify(userRepository).findByPhone("9876543210");
        verify(userRepository, never()).findByEmailNormalized(any());
    }

    @Test
    void login_disabled_account_is_rejected_after_password_check() {
        User user = new User();
        user.setEmail("s@example.com");
        user.setPasswordHash("hash");
        user.setStatus(UserStatus.INACTIVE);
        when(userRepository.findByEmailNormalized("s@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret123", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginRequest("s@example.com", "Secret123")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCOUNT_DISABLED);
        verifyNoInteractions(jwtService);
    }

    @Test
    void register_duplicate_email_is_conflict() {
        when(userRepository.existsByEmailNormalized("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegisterRequest("Dup User", " DUP@example.com ", null, "Secret123", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void forgot_password_for_unknown_email_is_silent() {
        when(userRepository.findByEmailNormalized("nobody@example.com")).thenReturn(Optional.empty());

        service.forgotPassword(new ForgotPasswordRequest("nobody@example.com"));

        verifyNoInteractions(tokenStore, events);
    }
}
