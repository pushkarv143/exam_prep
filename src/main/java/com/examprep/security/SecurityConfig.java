package com.examprep.security;

import com.examprep.audit.service.AuditService;
import com.examprep.audit.web.AuditFilter;
import com.examprep.common.exception.ApiErrorFactory;
import com.examprep.common.idempotency.IdempotencyAspect;
import com.examprep.rbac.service.PermissionService;
import com.examprep.security.ip.AdminAccessFilter;
import com.examprep.security.ip.IpAllowlistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import com.examprep.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * Stateless JWT security.
 * <ul>
 *   <li>URL rules below are coarse. Fine-grained permission checks use {@code @PreAuthorize("@perm.has(...)")}
 *       on controllers, enabled by {@code @EnableMethodSecurity}.</li>
 *   <li>CSRF is disabled because the API is authenticated only by a bearer header, never cookies.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/login/mfa",
            "/api/v1/auth/refresh",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/public/**",
            "/api/v1/payments/webhook/**",   // authenticated by the provider's HMAC signature instead
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",   // expose only on the internal network / ingress-blocked
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/error"
    };

    private final JwtService jwtService;
    private final TokenStore tokenStore;
    private final AppSecurityProperties securityProperties;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final IpAllowlistService ipAllowlist;
    private final PermissionService permissionService;
    private final ApiErrorFactory apiErrors;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // Coarse gate: any staff role that can open the portal. Each endpoint then checks
                        // its own permission with @PreAuthorize("@perm.has('...')").
                        .requestMatchers("/api/v1/admin/**").access(adminAccess())
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, tokenStore, securityProperties),
                        UsernamePasswordAuthenticationFilter.class)
                // Audit runs right after authentication, so it also records requests that are denied later.
                .addFilterAfter(new AuditFilter(auditService, objectMapper), JwtAuthenticationFilter.class)
                .addFilterAfter(new AdminAccessFilter(ipAllowlist, permissionService, securityProperties, apiErrors),
                        AuditFilter.class);
        return http.build();
    }

    private AuthorizationManager<RequestAuthorizationContext> adminAccess() {
        return (authentication, context) -> {
            Authentication auth = authentication.get();
            boolean ok = auth != null && auth.getPrincipal() instanceof AuthUser user
                    && permissionService.has(user, "admin.access");
            return new AuthorizationDecision(ok);
        };
    }

    /** The DB stores plain {@code $2a$}/{@code $2b$} bcrypt hashes (the seed data uses pgcrypto). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(securityProperties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "X-Request-Id",
                AuditFilter.REASON_HEADER, IdempotencyAspect.HEADER));
        config.setExposedHeaders(List.of("X-Request-Id", HttpHeaders.RETRY_AFTER, HttpHeaders.CONTENT_DISPOSITION,
                "Idempotent-Replayed",
                "X-RateLimit-Limit", "X-RateLimit-Remaining"));
        config.setAllowCredentials(false);
        config.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
