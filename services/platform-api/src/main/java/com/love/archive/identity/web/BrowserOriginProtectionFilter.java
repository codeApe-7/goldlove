package com.love.archive.identity.web;

import com.love.archive.common.web.RequestIdFilter;
import com.love.archive.identity.config.BrowserSecurityProperties;
import com.love.archive.identity.security.AuthLogics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class BrowserOriginProtectionFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final Set<String> allowedOrigins;
    private final Set<String> authCookieNames;

    public BrowserOriginProtectionFilter(BrowserSecurityProperties properties, AuthLogics authLogics) {
        this.allowedOrigins = properties.getAllowedOrigins().stream()
                .map(BrowserOriginProtectionFilter::removeTrailingSlash)
                .collect(Collectors.toUnmodifiableSet());
        this.authCookieNames = Set.of(
                authLogics.guest().getTokenName(),
                authLogics.admin().getTokenName());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod())
                || !hasAuthenticationCookie(request)
                || hasTrustedOrigin(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String requestId = RequestIdFilter.current(request);
        response.getWriter().write("{\"success\":false,\"code\":\"CSRF_ORIGIN_REJECTED\","
                + "\"message\":\"请求来源不受信任\",\"data\":null,\"requestId\":\""
                + requestId + "\"}");
    }

    private boolean hasAuthenticationCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (authCookieNames.contains(cookie.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTrustedOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null) {
            return allowedOrigins.contains(removeTrailingSlash(origin));
        }
        String referer = request.getHeader("Referer");
        if (referer == null) {
            return false;
        }
        try {
            URI uri = URI.create(referer);
            if (uri.getScheme() == null || uri.getRawAuthority() == null) {
                return false;
            }
            return allowedOrigins.contains(uri.getScheme() + "://" + uri.getRawAuthority());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String removeTrailingSlash(String origin) {
        String normalized = origin.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
