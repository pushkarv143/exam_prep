package com.examprep.common.exception;

import com.examprep.common.api.ApiError;
import com.examprep.common.api.ApiResponse;
import com.examprep.common.web.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
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

    private final ObjectMapper objectMapper;

    public ApiResponse<Void> build(ErrorCode code, String message, List<ApiError.FieldViolation> violations) {
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
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), build(code, message));
    }
}
