package com.love.archive.identity.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("app.membership")
public class MembershipProperties {

    /** 累计付费额度达到该阈值（分）时自动升级 SVIP。 */
    private long svipThresholdMinor = 59_900L;
}
