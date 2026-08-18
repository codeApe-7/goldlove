package com.love.archive.payment.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("app.payment.online")
public class OnlinePaymentProperties {

    /** 线上建档下单金额（分）。金额以后端配置为准，绝不信任前端传入。 */
    private long registrationAmountMinor = 100L;

    /** 下单商品描述。 */
    private String orderDescription = "婚恋智能档案库建档服务";

    /** 一次性注册令牌有效期。 */
    private Duration registrationTokenTtl = Duration.ofMinutes(30);
}
