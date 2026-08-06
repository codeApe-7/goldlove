package com.love.archive.identity.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("app.identity.authentication-rate-limit")
public class AuthenticationRateLimitProperties {

    private int accountMaxAttempts = 5;
    private Duration accountWindow = Duration.ofMinutes(15);
    private int clientMaxAttempts = 30;
    private Duration clientWindow = Duration.ofMinutes(1);

}
