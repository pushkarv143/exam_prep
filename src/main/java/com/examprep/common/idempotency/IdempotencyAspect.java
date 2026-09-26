package com.examprep.common.idempotency;

import com.examprep.common.api.ApiResponse;
import com.examprep.common.exception.BusinessException;
import com.examprep.common.exception.ErrorCode;
import com.examprep.common.util.Hashing;
import com.examprep.rbac.service.PermissionChecker;
import com.examprep.security.AuthUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Implements {@link Idempotent} on top of the {@code idempotency_keys} table. */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotencyAspect {

    public static final String HEADER = "Idempotency-Key";
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_.:-]{8,128}$");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return pjp.proceed();
        }
        HttpServletRequest request = attrs.getRequest();
        HttpServletResponse response = attrs.getResponse();
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank()) {
            if (idempotent.required()) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
            }
            return pjp.proceed();
        }
        if (!KEY.matcher(key).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Idempotency-Key must be 8-128 characters of A-Z a-z 0-9 _ . : -");
        }
        AuthUser user = PermissionChecker.currentUser();
        if (user == null) {
            return pjp.proceed();
        }
        String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String scope = request.getMethod() + " " + (pattern != null ? pattern : request.getRequestURI());
        String hash = Hashing.sha256Hex(scope + "|" + request.getRequestURI() + "|" + argsJson(pjp.getArgs()));

        int inserted = jdbc.update("""
                insert into idempotency_keys (user_id, idem_key, scope, request_hash, status, expires_at)
                values (?, ?, ?, ?, 'IN_PROGRESS', now() + interval '24 hours')
                on conflict (user_id, idem_key) do nothing
                """, user.id(), key, scope, hash);
        if (inserted == 0) {
            return replayOrReject(user, key, scope, hash, response);
        }
        try {
            Object result = pjp.proceed();
            int status = statusOf(pjp);
            jdbc.update("""
                    update idempotency_keys set status = 'COMPLETED', response_status = ?, response_body = ?::jsonb
                    where user_id = ? and idem_key = ?
                    """, status, json.writeValueAsString(result), user.id(), key);
            return result;
        } catch (Throwable t) {
            // The operation did not happen (or rolled back): free the key so the client can retry.
            jdbc.update("delete from idempotency_keys where user_id = ? and idem_key = ?", user.id(), key);
            throw t;
        }
    }

    private Object replayOrReject(AuthUser user, String key, String scope, String hash, HttpServletResponse response)
            throws Exception {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select scope, request_hash, status, response_status, response_body::text as body,
                       expires_at < now() as expired
                from idempotency_keys where user_id = ? and idem_key = ?
                """, user.id(), key);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_IN_PROGRESS);
        }
        Map<String, Object> row = rows.getFirst();
        if (Boolean.TRUE.equals(row.get("expired"))) {
            jdbc.update("delete from idempotency_keys where user_id = ? and idem_key = ?", user.id(), key);
            throw new BusinessException(ErrorCode.IDEMPOTENCY_IN_PROGRESS, "Idempotency key expired, please retry");
        }
        if (!scope.equals(row.get("scope")) || !hash.equals(((String) row.get("request_hash")).trim())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        if (!"COMPLETED".equals(row.get("status"))) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_IN_PROGRESS);
        }
        if (response != null) {
            Number status = (Number) row.get("response_status");
            if (status != null) {
                response.setStatus(status.intValue());
            }
            response.setHeader("Idempotent-Replayed", "true");
        }
        log.info("Replayed idempotent response for {} key {}", scope, key);
        JsonNode body = json.readTree((String) row.get("body"));
        return json.treeToValue(body, ApiResponse.class);
    }

    private String argsJson(Object[] args) {
        StringBuilder sb = new StringBuilder();
        for (Object a : args) {
            if (a == null || a instanceof AuthUser || a instanceof ServletRequest || a instanceof ServletResponse) {
                continue;
            }
            try {
                sb.append(json.writeValueAsString(a)).append('|');
            } catch (Exception e) {
                sb.append(a).append('|');
            }
        }
        return sb.toString();
    }

    private static int statusOf(ProceedingJoinPoint pjp) {
        ResponseStatus rs = AnnotatedElementUtils.findMergedAnnotation(
                ((MethodSignature) pjp.getSignature()).getMethod(), ResponseStatus.class);
        return rs == null ? HttpStatus.OK.value() : rs.code().value();
    }

    @Scheduled(cron = "0 41 * * * *")
    public void purgeExpired() {
        jdbc.update("delete from idempotency_keys where expires_at < now()");
    }
}
