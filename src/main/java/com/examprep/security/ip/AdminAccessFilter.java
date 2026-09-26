package com.examprep.security.ip;

import com.examprep.common.exception.ApiErrorFactory;
import com.examprep.common.exception.ErrorCode;
import com.examprep.rbac.service.PermissionService;
import com.examprep.security.AppSecurityProperties;
import com.examprep.security.AuthUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Gate for {@code /api/v1/admin/**}, applied after authentication (anonymous requests are
 * left to the normal 401 handling):
 * <ol>
 *   <li>the client IP must pass the admin IP allow-list (when enabled), and</li>
 *   <li>when {@code app.security.mfa.enforce-for-staff} is on, the session must have passed 2FA
 *       ({@code mfa} claim). Otherwise the response is 403 MFA_ENROLLMENT_REQUIRED, and the
 *       portal sends the user to the 2FA setup screen.</li>
 * </ol>
 * The {@code admin.access} permission itself is checked by the URL rule in SecurityConfig.
 */
@RequiredArgsConstructor
public class AdminAccessFilter extends OncePerRequestFilter {

    private final IpAllowlistService allowlist;
    private final PermissionService permissions;
    private final AppSecurityProperties props;
    private final ApiErrorFactory errors;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) {
            chain.doFilter(request, response);
            return;
        }
        if (!allowlist.isAllowed(request.getRemoteAddr())) {
            errors.write(response, ErrorCode.IP_NOT_ALLOWED, null);
            return;
        }
        if (props.mfa().enforceForStaff() && !user.mfaVerified() && permissions.has(user, "admin.access")) {
            errors.write(response, ErrorCode.MFA_ENROLLMENT_REQUIRED, null);
            return;
        }
        chain.doFilter(request, response);
    }
}
