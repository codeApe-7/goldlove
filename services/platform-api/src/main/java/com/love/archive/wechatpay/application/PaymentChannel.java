package com.love.archive.wechatpay.application;

import java.util.Optional;

/**
 * 支付渠道出站接口。业务侧只通过该接口下单、查单与验签回调，
 * 渠道实现细节（签名、证书、HTTP 往返）全部封闭在 wechatpay 模块内部。
 */
public interface PaymentChannel {

    /** 渠道是否已配置齐全；未配置时其余方法抛 PAYMENT_CHANNEL_NOT_CONFIGURED。 */
    boolean configured();

    CreateOrderResult createOrder(CreateOrderCommand command);

    Optional<PaymentResult> queryByOutTradeNo(String outTradeNo);

    /** 验签 + 解密回调资源；验签失败抛 PAYMENT_NOTIFY_SIGNATURE_INVALID。 */
    PaymentResult verifyAndDecodeNotify(NotifyPayload payload);
}
