package com.love.archive.payment.application;

import com.love.archive.payment.domain.PaymentChannelType;
import java.util.Optional;

/**
 * 支付渠道出站端口。业务侧只通过该接口下单、查单与验签回调，
 * 渠道实现细节（签名、证书、HTTP 往返）全部封闭在具体渠道模块内部。
 *
 * <p>payer 相关能力仅「需要前置授权」的渠道（如微信网页授权）实现；
 * 不需要授权前置的渠道（如易支付）在对应方法上抛出「不支持」。</p>
 */
public interface PaymentChannel {

    /** 渠道类型，用于落库与前端分发。 */
    PaymentChannelType kind();

    /** 渠道是否已配置齐全；未配置时其余方法抛 PAYMENT_CHANNEL_NOT_CONFIGURED。 */
    boolean configured();

    /** 下单前是否需要先做 payer 授权（微信为 true，易支付为 false）。 */
    boolean requiresPayerAuthorization();

    /** 返回前端跳转授权的地址；仅 requiresPayerAuthorization() == true 时有效。 */
    String payerAuthorizationUrl(String state);

    /** 用授权码换取 payer 标识；仅 requiresPayerAuthorization() == true 时有效。 */
    String resolvePayer(String authorizationCode);

    CreateOrderResult createOrder(CreateOrderCommand command);

    Optional<PaymentResult> queryByOutTradeNo(String outTradeNo);

    /** 验签 + 解析回调；验签失败抛 PAYMENT_NOTIFY_SIGNATURE_INVALID。 */
    PaymentResult verifyAndDecodeNotify(NotifyPayload payload);
}
