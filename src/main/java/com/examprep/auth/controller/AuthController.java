package com.examprep.auth.controller;

import com.examprep.auth.dto.AuthResponse;
import com.examprep.auth.dto.ForgotPasswordRequest;
import com.examprep.auth.dto.LoginRequest;
import com.examprep.auth.dto.LogoutRequest;
import com.examprep.auth.dto.RefreshTokenRequest;
import com.examprep.auth.dto.RegisterRequest;
import com.examprep.auth.dto.ResetPasswordRequest;
import com.examprep.auth.service.AuthService;
import com.examprep.common.api.ApiResponse;
import com.examprep.common.ratelimit.RateLimit;
import com.examprep.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Registration, login, token refresh, password reset")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a new student account (auto-login)")
    @SecurityRequirements
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimit(name = "register", limit = 10, windowSeconds = 3600)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @Operation(summary = "Log in with email or phone + password")
    @SecurityRequirements
    @PostMapping("/login")
    @RateLimit(name = "login", limit = 10, windowSeconds = 60)
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @Operation(summary = "Rotate tokens using a refresh token (single use)")
    @SecurityRequirements
    @PostMapping("/refresh")
    @RateLimit(name = "refresh", limit = 30, windowSeconds = 60)
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "Log out: revoke the current access token and the given refresh token")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal AuthUser user,
                                    @RequestBody(required = false) LogoutRequest request) {
        authService.logout(user, request == null ? null : request.refreshToken());
        return ApiResponse.ok();
    }

    @Operation(summary = "Request a password-reset email (always returns 200)")
    @SecurityRequirements
    @PostMapping("/forgot-password")
    @RateLimit(name = "forgot-password", limit = 5, windowSeconds = 900)
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ApiResponse.ok();
    }

    @Operation(summary = "Set a new password using the emailed token")
    @SecurityRequirements
    @PostMapping("/reset-password")
    @RateLimit(name = "reset-password", limit = 10, windowSeconds = 900)
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.ok();
    }
}
