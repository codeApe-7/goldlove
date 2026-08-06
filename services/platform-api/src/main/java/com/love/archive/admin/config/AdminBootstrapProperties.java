package com.love.archive.admin.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("app.admin.bootstrap")
public class AdminBootstrapProperties {

    private boolean enabled;
    private String username;
    private String displayName;
    private String password;

}
