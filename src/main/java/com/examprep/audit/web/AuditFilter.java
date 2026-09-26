package com.examprep.audit.web;

import com.examprep.audit.service.AuditContext;
import com.examprep.audit.service.AuditRecord;
import com.examprep.audit.service.AuditService;
import com.examprep.common.exception.ApiErrorFactory;
import com.examprep.common.web.RequestIdFilter;
import com.examprep.security.AuthUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Writes one {@code audit_log} row per audited request, after the request completes:
 * <ul>
 *   <li>every write (POST/PUT/PATCH/DELETE) under {@code /api/v1/admin/**}, including
 *       rejected ones (403 → DENIED, 4xx/5xx → FAILURE), and</li>
 *   <li>any other request where code called {@link AuditContext#force()}.</li>
 * </ul>
 * The actor, IP, user agent, request id, masked request body and the {@code X-Reason}
 * header are captured here. Services add a semantic action name and before/after snapshots
 * through {@link AuditContext}.
 *
 * <p>It runs inside the security chain, right after JWT authentication, so the principal is
 * known and authorisation failures from later filters are still recorded.
 */
@RequiredArgsConstructor
public class AuditFilter extends OncePerRequestFilter {

    public static final String REASON_HEADER = "X-Reason";
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final List<String> ID_VARIABLES = List.of("id", "testId", "questionId", "userId", "attemptId",
            "seriesId", "sectionId", "testQuestionId", "jobId", "roleName", "name");
    private static final int MAX_BODY = 64 * 1024;

    private final AuditService audit;
    private final ObjectMapper json;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!WRITE_METHODS.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        boolean adminWrite = request.getRequestURI().startsWith("/api/v1/admin/");
        HttpServletRequest req = adminWrite && !isMultipart(request)
                ? new ContentCachingRequestWrapper(request, MAX_BODY) : request;

        AuditContext.Capture capture = new AuditContext.Capture();
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            attrs.setAttribute(AuditContext.ATTRIBUTE, capture, RequestAttributes.SCOPE_REQUEST);
        }
        RuntimeException failure = null;
        try {
            chain.doFilter(req, response);
        } catch (RuntimeException | IOException | ServletException e) {
            if (e instanceof RuntimeException re) {
                failure = re;
            }
            throw e;
        } finally {
            if (adminWrite || capture.forced()) {
                write(req, response, capture, failure != null);
            }
        }
    }

    private void write(HttpServletRequest req, HttpServletResponse res, AuditContext.Capture c, boolean threw) {
        int status = threw ? 500 : res.getStatus();
        AuditRecord.Outcome outcome = status == 401 || status == 403 ? AuditRecord.Outcome.DENIED
                : status >= 400 ? AuditRecord.Outcome.FAILURE : AuditRecord.Outcome.SUCCESS;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        AuthUser user = auth != null && auth.getPrincipal() instanceof AuthUser u ? u : null;

        @SuppressWarnings("unchecked")
        Map<String, String> vars = (Map<String, String>) req.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String pattern = (String) req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path = req.getRequestURI();

        ObjectNode metadata = json.valueToTree(c.metadata());
        JsonNode body = body(req);
        if (body != null) {
            metadata.set("request", body);
        }
        String reason = header(req, REASON_HEADER);
        if (reason == null && body != null && body.hasNonNull("reason") && body.get("reason").isTextual()) {
            reason = body.get("reason").asText();
        }

        audit.record(AuditRecord.builder()
                .actorId(user == null ? null : user.id())
                .actorEmail(user == null ? null : user.email())
                .actorRoles(user == null ? null : user.roles())
                .action(c.action() != null ? c.action()
                        : req.getMethod() + " " + stripPrefix(pattern != null ? pattern : path))
                .entityType(c.entityType() != null ? c.entityType() : entityType(path))
                .entityId(c.entityId() != null ? c.entityId() : entityId(vars))
                .outcome(outcome)
                .httpMethod(req.getMethod())
                .path(path)
                .statusCode(status)
                .errorCode(errorCode(req, res))
                .reason(reason)
                .before(audit.snapshot(c.before()))
                .after(audit.snapshot(c.after()))
                .metadata(metadata)
                .ip(req.getRemoteAddr())
                .userAgent(req.getHeader("User-Agent"))
                .requestId(MDC.get(RequestIdFilter.MDC_KEY))
                .build());
    }

    private JsonNode body(HttpServletRequest req) {
        if (!(req instanceof ContentCachingRequestWrapper wrapper)) {
            return null;
        }
        byte[] bytes = wrapper.getContentAsByteArray();
        if (bytes.length == 0) {
            return null;
        }
        try {
            return json.readTree(bytes);
        } catch (IOException e) {
            return json.getNodeFactory().textNode("(non-JSON body, " + bytes.length + " bytes)");
        }
    }

    private static String errorCode(HttpServletRequest req, HttpServletResponse res) {
        Object code = req.getAttribute(ApiErrorFactory.ERROR_CODE_ATTRIBUTE);
        return code != null ? code.toString() : res.getHeader("X-Error-Code");
    }

    private static String header(HttpServletRequest req, String name) {
        String v = req.getHeader(name);
        if (v == null || v.isBlank()) {
            return null;
        }
        return URLDecoder.decode(v, StandardCharsets.UTF_8).trim();
    }

    private static boolean isMultipart(HttpServletRequest req) {
        String ct = req.getContentType();
        return ct != null && ct.toLowerCase(Locale.ROOT).startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    private static String stripPrefix(String p) {
        return p.startsWith("/api/v1") ? p.substring(7) : p;
    }

    private static final Map<String, String> ENTITY_TYPES = Map.ofEntries(
            Map.entry("series", "SERIES"), Map.entry("approvals", "APPROVAL_REQUEST"),
            Map.entry("audit", "AUDIT"), Map.entry("security", "SECURITY"), Map.entry("catalog", "CATALOG"),
            Map.entry("dashboard", "DASHBOARD"));

    /** "/api/v1/admin/tests/..." → TEST, "/admin/batches" → BATCH, "/admin/approval-policies/x" → APPROVAL_POLICY. */
    static String entityType(String path) {
        String[] parts = path.split("/");
        int i = List.of(parts).indexOf("admin");
        if (i < 0 || i + 1 >= parts.length) {
            return null;
        }
        String seg = parts[i + 1].toLowerCase(Locale.ROOT);
        String known = ENTITY_TYPES.get(seg);
        if (known != null) {
            return known;
        }
        String singular = seg.endsWith("ies") ? seg.substring(0, seg.length() - 3) + "y"
                : seg.matches(".*(ches|shes|xes|sses)$") ? seg.substring(0, seg.length() - 2)
                : seg.endsWith("s") ? seg.substring(0, seg.length() - 1) : seg;
        return singular.toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static String entityId(Map<String, String> vars) {
        if (vars == null || vars.isEmpty()) {
            return null;
        }
        for (String name : ID_VARIABLES) {
            if (vars.containsKey(name)) {
                return vars.get(name);
            }
        }
        return vars.values().iterator().next();
    }
}
