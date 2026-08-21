package com.love.archive.payment.application;

import com.love.archive.payment.domain.PaymentChannelType;
import java.util.Optional;

/**
 * 支付渠道出站端口。业务侧只通过该接口下单、查单与验签回调，
 * 渠道实现细节（签名、HTTP 往返）全部封闭在具体渠道模块内部。
 */
public interface PaymentChannel {

    /** 渠道类型，用于落库与前端分发。 */
    PaymentChannelType kind();

    /** 渠道是否已配置齐全；未配置时其余方法抛 PAYMENT_CHANNEL_NOT_CONFIGURED。 */
    boolean configured();

    CreateOrderResult createOrder(CreateOrderCommand command);

    Optional<PaymentResult> queryByOutTradeNo(String outTradeNo);

    /** 验签 + 解析回调；验签失败抛 PAYMENT_NOTIFY_SIGNATURE_INVALID。 */
    PaymentResult verifyAndDecodeNotify(NotifyPayload payload);
}
