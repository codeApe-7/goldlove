package com.love.archive.identity.web;

import cn.dev33.satoken.interceptor.SaInterceptor;
import com.love.archive.identity.security.AuthLogics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthInterceptorConfiguration implements WebMvcConfigurer {

    private final AuthLogics authLogics;

    public AuthInterceptorConfiguration(AuthLogics authLogics) {
        this.authLogics = authLogics;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(ignored -> authLogics.admin().checkLogin()))
                .addPathPatterns("/api/v1/admin/**")
                .excludePathPatterns("/api/v1/admin/auth/login");

        registry.addInterceptor(new SaInterceptor(ignored -> authLogics.guest().checkLogin()))
                .addPathPatterns("/api/v1/guest/**")
                .excludePathPatterns(
                        "/api/v1/guest/auth/login",
                        "/api/v1/guest/auth/activate");
    }
}
