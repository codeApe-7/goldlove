package com.love.archive.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("app.payment.online")
public class OnlinePaymentProperties {

    /**
     * VIP 升级下单金额（分）的**回落值**。金额以后端为准，绝不信任前端传入。
     *
     * <p>管理后台在 {@code payment_setting} 里设过金额之后，用的是那一行；
     * 这个配置只在后台从未设置过时生效。见 {@code PaymentSettingService}。</p>
     */
    private long vipUpgradeAmountMinor = 100L;

    /** 下单商品描述。 */
    private String orderDescription = "gold 智能档案库 VIP 会员";

    /** 启用的线上支付渠道（当前只有 XPAY_ALIPAY）。为空时取唯一已配置渠道。 */
    private String provider;

    /**
     * 订单可支付时长（分钟）。到点仍未支付即转 {@code CLOSED}，前端据此提示重新下单。
     *
     * <p>默认 5 分钟，对齐易支付收银台自己的超时（它过期后查单返回
     * {@code code=1 / 没有找到订单信息}，收银台页面则回 {@code 502 未能找到订单信息或失效}）。
     * 宁可与渠道齐平或略短，也不要更长——更长就会在界面上说「还能付」而实际点过去是一个死掉的
     * 收银台。</p>
     *
     * <p>关单只是界面口径，渠道才是权威：真在关单之后付成了，回调与补偿查单
     * <b>依然会把 CLOSED 的订单结算掉</b>（见 {@code PaymentOrderStore#settle}），钱到了就认。
     * 补偿查单也是先问渠道、只有渠道说没付才关单，不会抢在渠道前面把一笔已付的订单关掉。</p>
     *
     * <p>配成非正数即视为不过期——免得一个手滑的 0 让每笔新订单当场作废。</p>
     */
    private long orderExpiryMinutes = 5L;
}
