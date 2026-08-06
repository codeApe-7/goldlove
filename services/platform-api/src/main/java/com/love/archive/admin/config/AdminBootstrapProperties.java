package com.love.archive.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.admin.bootstrap")
public class AdminBootstrapProperties {

    private boolean enabled;
    private String username;
    private String displayName;
    private String password;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
