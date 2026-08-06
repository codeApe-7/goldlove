package com.love.archive.common.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("app.sensitive-security")
public class SensitiveSecurityProperties {

    private String encryptionKey;
    private String hmacKey;
}
