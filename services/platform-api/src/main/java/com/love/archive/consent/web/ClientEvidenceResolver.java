package com.love.archive.consent.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientEvidenceResolver {

    public String clientIp(HttpServletRequest request) {
        // The container changes remoteAddr only when its trusted forward-header strategy is enabled.
        // Do not read Forwarded or X-Forwarded-For directly: untrusted clients can forge them.
        return request.getRemoteAddr();
    }

    public String userAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }
}
