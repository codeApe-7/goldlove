package com.love.archive.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("app.payment.online")
public class OnlinePaymentProperties {

    /** VIP 升级下单金额（分）。金额以后端配置为准，绝不信任前端传入。 */
    private long vipUpgradeAmountMinor = 100L;

    /** 下单商品描述。 */
    private String orderDescription = "gold 智能档案库 VIP 会员";

    /** 启用的线上支付渠道（当前只有 XPAY_ALIPAY）。为空时取唯一已配置渠道。 */
    private String provider;
}
