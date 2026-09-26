package com.examprep.security;

import com.examprep.common.exception.ErrorCode;
import com.examprep.security.jwt.InvalidTokenException;
import com.examprep.security.jwt.JwtService;
import com.examprep.security.jwt.TokenClaims;
import com.examprep.security.jwt.TokenType;
import com.examprep.user.entity.Roles;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates requests that carry {@code Authorization: Bearer <access token>}.
 *
 * <p>An invalid, expired or revoked token does NOT fail the request here. The error
 * code is stored as a request attribute and the request continues anonymously. Public
 * endpoints still work, and protected ones are rejected by
 * {@link RestAuthenticationEntryPoint}, which reports the precise reason
 * (TOKEN_EXPIRED makes the frontend refresh; SESSION_REVOKED makes it log out).
 *
 * <p>This filter is deliberately not a Spring bean. A bean would also be registered
 * as a plain servlet filter and run outside the security chain.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".error";
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final TokenStore tokenStore;
    private final AppSecurityProperties securityProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            TokenClaims claims = jwtService.parse(header.substring(BEARER.length()).trim(), TokenType.ACCESS);
            verifyNotRevoked(claims);
            authenticate(request, claims);
        } catch (InvalidTokenException e) {
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, e.getErrorCode());
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }

    private void verifyNotRevoked(TokenClaims claims) {
        TokenStore.TokenState state;
        try {
            state = tokenStore.loadState(claims.tokenId(), claims.userId(), claims.sessionId());
        } catch (DataAccessException e) {
            // Fail open on the revocation check only. The signature and expiry are
            // already verified and access tokens are short-lived, so a Redis blip must
            // not log out 50k students mid-exam.
            log.warn("Token revocation check skipped, Redis unavailable: {}", e.getMessage());
            return;
        }
        if (state.blacklisted() || claims.generation() < state.generation()) {
            throw new InvalidTokenException(ErrorCode.SESSION_REVOKED);
        }
        if (securityProperties.singleSessionEnabled()
                && claims.roles().contains(Roles.STUDENT)
                && state.activeSessionId() != null
                && !state.activeSessionId().equals(claims.sessionId())) {
            throw new InvalidTokenException(ErrorCode.SESSION_REVOKED,
                    "You have logged in on another device. This session has ended.");
        }
    }

    private void authenticate(HttpServletRequest request, TokenClaims claims) {
        AuthUser principal = new AuthUser(claims.userId(), claims.email(), claims.roles(), claims.sessionId(),
                claims.tokenId(), claims.expiresAt(), claims.mfaVerified());
        List<SimpleGrantedAuthority> authorities = claims.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
