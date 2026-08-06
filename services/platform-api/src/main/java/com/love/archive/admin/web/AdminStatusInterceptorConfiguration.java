package com.love.archive.admin.web;

import com.love.archive.admin.application.AdminAuthService;
import com.love.archive.common.web.ApiException;
import com.love.archive.identity.security.AuthLogics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequiredArgsConstructor
public class AdminStatusInterceptorConfiguration implements WebMvcConfigurer {

    private final AdminAuthService adminAuthService;
    private final AuthLogics authLogics;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        HandlerInterceptor activeAdmin = new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                long adminId = authLogics.admin().getLoginIdAsLong();
                try {
                    adminAuthService.requireActive(adminId);
                } catch (ApiException exception) {
                    authLogics.admin().kickout(adminId);
                    throw exception;
                }
                return true;
            }
        };
        registry.addInterceptor(activeAdmin)
                .addPathPatterns("/api/v1/admin/**")
                .excludePathPatterns("/api/v1/admin/auth/login")
                .order(-90);
    }
}
