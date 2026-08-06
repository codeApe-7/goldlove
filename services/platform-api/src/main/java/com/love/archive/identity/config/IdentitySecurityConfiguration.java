package com.love.archive.identity.config;

import com.love.archive.identity.domain.PhoneNormalizer;
import com.love.archive.identity.security.Argon2PasswordHasher;
import com.love.archive.identity.security.InitialCredentialGenerator;
import com.love.archive.identity.security.PasswordHasher;
import java.security.SecureRandom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IdentitySecurityConfiguration {

    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Bean
    PasswordHasher passwordHasher(SecureRandom secureRandom, IdentitySecurityProperties properties) {
        IdentitySecurityProperties.Argon2 argon2 = properties.getArgon2();
        return new Argon2PasswordHasher(
                secureRandom,
                argon2.getMemoryKiB(),
                argon2.getIterations(),
                argon2.getParallelism(),
                argon2.getSaltLength(),
                argon2.getHashLength());
    }

    @Bean
    InitialCredentialGenerator initialCredentialGenerator(
            SecureRandom secureRandom,
            IdentitySecurityProperties properties) {
        return new InitialCredentialGenerator(secureRandom, properties.getInitialCredentialLength());
    }

    @Bean
    PhoneNormalizer phoneNormalizer() {
        return new PhoneNormalizer();
    }
}
