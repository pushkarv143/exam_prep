package com.examprep.common.exception;

import com.examprep.common.api.ApiError;
import com.examprep.common.api.ApiResponse;
import com.examprep.common.web.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Builds error envelopes. It is shared by the MVC exception handler and by the
 * Spring Security entry point / access-denied handler, which run outside
 * DispatcherServlet. Every error therefore has the same JSON shape.
 */
@Component
@RequiredArgsConstructor
public class ApiErrorFactory {

    /** Request attribute holding the error code of the response (read by the audit filter). */
    public static final String ERROR_CODE_ATTRIBUTE = ApiErrorFactory.class.getName() + ".code";

    private final ObjectMapper objectMapper;

    public ApiResponse<Void> build(ErrorCode code, String message, List<ApiError.FieldViolation> violations) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            attrs.setAttribute(ERROR_CODE_ATTRIBUTE, code.name(),
                    RequestAttributes.SCOPE_REQUEST);
        }
        return ApiResponse.failure(new ApiError(code.name(),
                message != null ? message : code.getDefaultMessage(),
                violations, MDC.get(RequestIdFilter.MDC_KEY)));
    }

    public ApiResponse<Void> build(ErrorCode code, String message) {
        return build(code, message, null);
    }

    /** Writes an error directly to the servlet response (used from filters). */
    public void write(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setHeader("X-Error-Code", code.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), build(code, message));
    }
}
