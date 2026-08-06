package com.love.archive.common.security;

import java.security.SecureRandom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SensitiveSecurityConfiguration {

    @Bean
    SensitiveValueProtector sensitiveValueProtector(
            SecureRandom secureRandom,
            SensitiveSecurityProperties properties) {
        return new SensitiveValueProtector(
                properties.getEncryptionKey(),
                properties.getHmacKey(),
                secureRandom);
    }
}
