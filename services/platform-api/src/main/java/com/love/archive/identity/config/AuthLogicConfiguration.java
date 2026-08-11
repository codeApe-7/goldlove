package com.love.archive.identity.config;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.config.SaTokenConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
public class AuthLogicConfiguration {

    @Bean
    @Primary
    StpLogic guestStpLogic(SaTokenConfig globalConfig) {
        SaTokenConfig isolated = new SaTokenConfig()
                .setTokenName("Authorization")
                .setTimeout(globalConfig.getTimeout())
                .setActiveTimeout(globalConfig.getActiveTimeout())
                .setIsConcurrent(globalConfig.getIsConcurrent())
                .setIsShare(globalConfig.getIsShare())
                .setIsReadBody(globalConfig.getIsReadBody())
                .setIsReadHeader(true)
                .setIsReadCookie(false)
                .setIsLastingCookie(globalConfig.getIsLastingCookie())
                .setIsWriteHeader(globalConfig.getIsWriteHeader())
                .setTokenStyle(globalConfig.getTokenStyle())
                .setTokenPrefix("Bearer")
                .setAutoRenew(globalConfig.getAutoRenew())
                .setCookie(globalConfig.getCookie());
        return new StpLogic("guest").setConfig(isolated);
    }

    @Bean
    StpLogic adminStpLogic(SaTokenConfig globalConfig) {
        return logic("admin", globalConfig);
    }

    private static StpLogic logic(String loginType, SaTokenConfig global) {
        SaTokenConfig isolated = new SaTokenConfig()
                .setTokenName(global.getTokenName() + "-" + loginType)
                .setTimeout(global.getTimeout())
                .setActiveTimeout(global.getActiveTimeout())
                .setIsConcurrent(global.getIsConcurrent())
                .setIsShare(global.getIsShare())
                .setIsReadBody(global.getIsReadBody())
                .setIsReadHeader(global.getIsReadHeader())
                .setIsReadCookie(global.getIsReadCookie())
                .setIsLastingCookie(global.getIsLastingCookie())
                .setIsWriteHeader(global.getIsWriteHeader())
                .setTokenStyle(global.getTokenStyle())
                .setAutoRenew(global.getAutoRenew())
                .setCookie(global.getCookie());
        return new StpLogic(loginType).setConfig(isolated);
    }
}
