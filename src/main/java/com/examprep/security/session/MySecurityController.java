package com.examprep.security.session;

import com.examprep.audit.service.AuditContext;
import com.examprep.common.api.ApiResponse;
import com.examprep.common.ratelimit.RateLimit;
import com.examprep.security.AuthUser;
import com.examprep.security.mfa.MfaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** The signed-in user's own security: 2FA and devices. Available to every user, not only staff. */
@Tag(name = "My security")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MySecurityController {

    private final MfaService mfa;
    private final SessionService sessions;

    public record CodeRequest(@NotBlank @Size(max = 20) String code) {
    }

    @Operation(summary = "2FA status")
    @GetMapping("/mfa")
    public ApiResponse<MfaService.MfaStatus> mfaStatus(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(mfa.status(user.id()));
    }

    @Operation(summary = "Start 2FA setup: returns the secret and an otpauth:// URI for the QR code")
    @PostMapping("/mfa/setup")
    public ApiResponse<MfaService.MfaSetup> setup(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(mfa.beginSetup(user.id(), user.email()));
    }

    @Operation(summary = "Confirm setup with the first code; returns recovery codes (shown once)")
    @PostMapping("/mfa/confirm")
    @RateLimit(name = "mfa-confirm", limit = 10, windowSeconds = 300)
    public ApiResponse<Map<String, List<String>>> confirm(@AuthenticationPrincipal AuthUser user,
                                                          @Valid @RequestBody CodeRequest body) {
        List<String> codes = mfa.confirmSetup(user.id(), body.code());
        // This session just proved possession of the authenticator: mark it 2FA-verified,
        // so the next token refresh unlocks the admin portal without a new login.
        sessions.markMfaVerified(user.sessionId());
        return ApiResponse.ok(Map.of("recoveryCodes", codes));
    }

    @Operation(summary = "New recovery codes (needs a current code)")
    @PostMapping("/mfa/recovery-codes")
    @RateLimit(name = "mfa-confirm", limit = 10, windowSeconds = 300)
    public ApiResponse<Map<String, List<String>>> regenerate(@AuthenticationPrincipal AuthUser user,
                                                             @Valid @RequestBody CodeRequest body) {
        return ApiResponse.ok(Map.of("recoveryCodes", mfa.regenerateRecoveryCodes(user.id(), body.code())));
    }

    @Operation(summary = "Turn 2FA off (needs a current code)")
    @PostMapping("/mfa/disable")
    @RateLimit(name = "mfa-confirm", limit = 10, windowSeconds = 300)
    public ApiResponse<Void> disable(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CodeRequest body) {
        mfa.disable(user.id(), body.code());
        return ApiResponse.ok();
    }

    @Operation(summary = "My active sessions (devices)")
    @GetMapping("/sessions")
    public ApiResponse<List<SessionService.SessionDto>> list(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(sessions.active(user.id(), user.sessionId()));
    }

    @Operation(summary = "Log out one device")
    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> revoke(@AuthenticationPrincipal AuthUser user, @PathVariable String sessionId) {
        sessions.revoke(user.id(), sessionId, "user");
        AuditContext.force();
        AuditContext.action("session.revoke");
        AuditContext.entity("SESSION", sessionId);
        return ApiResponse.ok();
    }

    @Operation(summary = "Log out all devices", description = "keepCurrent=true keeps this session signed in")
    @PostMapping("/sessions/revoke-all")
    public ApiResponse<Map<String, Integer>> revokeAll(@AuthenticationPrincipal AuthUser user,
                                                       @RequestParam(defaultValue = "false") boolean keepCurrent) {
        int n = sessions.revokeAll(user.id(), keepCurrent ? user.sessionId() : null, "user: log out all devices");
        AuditContext.force();
        AuditContext.action("session.revoke-all");
        AuditContext.entity("USER", user.id());
        return ApiResponse.ok(Map.of("revoked", n));
    }
}
