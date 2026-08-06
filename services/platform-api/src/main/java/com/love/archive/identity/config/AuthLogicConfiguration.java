package com.love.archive.identity.config;

import cn.dev33.satoken.stp.StpLogic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
public class AuthLogicConfiguration {

    @Bean
    @Primary
    StpLogic guestStpLogic() {
        return new StpLogic("guest");
    }

    @Bean
    StpLogic adminStpLogic() {
        return new StpLogic("admin");
    }
}
