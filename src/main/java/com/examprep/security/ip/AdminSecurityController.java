package com.examprep.security.ip;

import com.examprep.common.api.ApiResponse;
import com.examprep.security.AuthUser;
import com.examprep.security.mfa.MfaService;
import com.examprep.security.session.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Security settings: admin IP allow-list, another user's 2FA reset, and their sessions. */
@Tag(name = "Security settings (admin)")
@RestController
@RequestMapping("/api/v1/admin/security")
@RequiredArgsConstructor
public class AdminSecurityController {

    private final IpAllowlistService allowlist;
    private final MfaService mfa;
    private final SessionService sessions;

    public record AddEntryRequest(@NotBlank @Size(max = 64) String cidr, @NotBlank @Size(max = 120) String label) {
    }

    public record EnableRequest(boolean enabled) {
    }

    @Operation(summary = "IP allow-list state, including whether your current IP matches")
    @PreAuthorize("@perm.has('security.manage')")
    @GetMapping("/ip-allowlist")
    public ApiResponse<IpAllowlistService.IpAllowlistState> state(HttpServletRequest request) {
        return ApiResponse.ok(allowlist.state(request.getRemoteAddr()));
    }

    @Operation(summary = "Add an IP address or CIDR range")
    @PreAuthorize("@perm.has('security.manage')")
    @PostMapping("/ip-allowlist")
    public ApiResponse<IpAllowlistService.IpAllowlistEntry> add(@AuthenticationPrincipal AuthUser user,
                                                     @Valid @RequestBody AddEntryRequest body) {
        return ApiResponse.ok(allowlist.add(body.cidr(), body.label(), user));
    }

    @Operation(summary = "Remove an entry")
    @PreAuthorize("@perm.has('security.manage')")
    @DeleteMapping("/ip-allowlist/{id}")
    public ApiResponse<Void> remove(@PathVariable UUID id) {
        allowlist.remove(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "Turn the allow-list on or off (refused if it would lock you out)")
    @PreAuthorize("@perm.has('security.manage')")
    @PutMapping("/ip-allowlist/enabled")
    public ApiResponse<IpAllowlistService.IpAllowlistState> enable(@AuthenticationPrincipal AuthUser user,
                                                        @RequestBody EnableRequest body, HttpServletRequest request) {
        return ApiResponse.ok(allowlist.setEnabled(body.enabled(), request.getRemoteAddr(), user));
    }

    @Operation(summary = "Reset a user's 2FA (lost phone); they must enrol again")
    @PreAuthorize("@perm.has('security.manage')")
    @PostMapping("/users/{userId}/mfa/reset")
    public ApiResponse<Void> resetMfa(@PathVariable UUID userId) {
        mfa.adminReset(userId);
        sessions.revokeAll(userId, null, "2FA reset by admin");
        return ApiResponse.ok();
    }

    @Operation(summary = "A user's 2FA status and active sessions")
    @PreAuthorize("@perm.any('security.manage', 'student.view', 'user.view')")
    @GetMapping("/users/{userId}")
    public ApiResponse<Map<String, Object>> userSecurity(@PathVariable UUID userId) {
        List<SessionService.SessionDto> active = sessions.active(userId, null);
        return ApiResponse.ok(Map.of("mfa", mfa.status(userId), "sessions", active));
    }

    @Operation(summary = "Log a user out of all devices")
    @PreAuthorize("@perm.any('security.manage', 'user.status')")
    @PostMapping("/users/{userId}/sessions/revoke-all")
    public ApiResponse<Map<String, Integer>> revokeAll(@PathVariable UUID userId) {
        return ApiResponse.ok(Map.of("revoked", sessions.revokeAll(userId, null, "admin: log out all devices")));
    }
}
