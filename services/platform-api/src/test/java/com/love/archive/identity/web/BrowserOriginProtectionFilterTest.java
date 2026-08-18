package com.love.archive.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpLogic;
import com.love.archive.identity.config.BrowserSecurityProperties;
import com.love.archive.identity.security.AuthLogics;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class BrowserOriginProtectionFilterTest {

    private static final String AUTH_COOKIE = "archive-token";

    @Test
    void wildcardAllowsAnyOrigin() throws Exception {
        BrowserOriginProtectionFilter filter = filterWithAllowedOrigins(List.of("*"));

        MockHttpServletRequest request = mutationWithCookieAndOrigin("https://evil.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(403);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void concreteOriginStillRejectsUntrustedOrigin() throws Exception {
        BrowserOriginProtectionFilter filter = filterWithAllowedOrigins(List.of("https://h5.example.test"));

        MockHttpServletRequest request = mutationWithCookieAndOrigin("https://evil.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    private MockHttpServletRequest mutationWithCookieAndOrigin(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/accounts");
        request.setCookies(new Cookie(AUTH_COOKIE, "some-token"));
        request.addHeader("Origin", origin);
        return request;
    }

    private BrowserOriginProtectionFilter filterWithAllowedOrigins(List<String> origins) {
        BrowserSecurityProperties properties = new BrowserSecurityProperties();
        properties.setAllowedOrigins(origins);
        AuthLogics authLogics = mock(AuthLogics.class);
        StpLogic adminLogic = mock(StpLogic.class);
        when(adminLogic.getTokenName()).thenReturn(AUTH_COOKIE);
        when(authLogics.admin()).thenReturn(adminLogic);
        return new BrowserOriginProtectionFilter(properties, authLogics);
    }
}
