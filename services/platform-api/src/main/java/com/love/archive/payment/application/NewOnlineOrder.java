package com.love.archive.payment.application;

import com.love.archive.payment.domain.PaymentChannelType;

/**
 * 待落库的线上订单。字段较多且相邻同类型参数容易传错位，故用 record 传递。
 *
 * @param outTradeNo                       商户订单号，全局唯一并作为幂等锚点
 * @param payer                            支付者标识（微信 openid）；无授权前置的渠道为 null
 * @param phoneToken                       手机号的不可逆比对令牌，来自 {@link RegistrationEligibilityPort}
 * @param channel                          支付渠道
 * @param presentedAuthorizationDocumentId 用户付款前看到的授权书主键
 * @param amountMinor                      下单金额（分），取服务端配置
 * @param description                      商品描述
 */
record NewOnlineOrder(
        String outTradeNo,
        String payer,
        String phoneToken,
        PaymentChannelType channel,
        long presentedAuthorizationDocumentId,
        long amountMinor,
        String description) {
}
