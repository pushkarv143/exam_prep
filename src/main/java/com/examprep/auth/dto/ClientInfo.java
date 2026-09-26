package com.examprep.auth.dto;

import jakarta.servlet.http.HttpServletRequest;

/** Where a login comes from: shown in the device list and recorded in the audit log. */
public record ClientInfo(String ip, String userAgent) {

    public static ClientInfo of(HttpServletRequest request) {
        return new ClientInfo(request.getRemoteAddr(), request.getHeader("User-Agent"));
    }
}
