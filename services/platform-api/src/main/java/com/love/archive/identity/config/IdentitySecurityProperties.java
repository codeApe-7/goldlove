package com.love.archive.identity.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("app.identity.security")
public class IdentitySecurityProperties {

    private final Argon2 argon2 = new Argon2();

    @Getter
    @Setter
    public static class Argon2 {

        private int memoryKiB = 65_536;
        private int iterations = 3;
        private int parallelism = 1;
        private int saltLength = 16;
        private int hashLength = 32;

    }
}
