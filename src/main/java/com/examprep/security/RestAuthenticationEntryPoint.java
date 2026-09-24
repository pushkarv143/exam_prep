package com.examprep.security;

import com.examprep.common.exception.ApiErrorFactory;
import com.examprep.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 401 in the standard JSON envelope, with the precise reason detected by the JWT filter. */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorFactory errors;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object reason = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        ErrorCode code = reason instanceof ErrorCode ec ? ec : ErrorCode.UNAUTHORIZED;
        errors.write(response, code, null);
    }
}
