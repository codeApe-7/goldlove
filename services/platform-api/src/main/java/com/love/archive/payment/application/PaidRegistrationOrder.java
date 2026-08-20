package com.love.archive.payment.application;

/**
 * 已支付且尚未用于注册的订单，供 identity 完成线上建档。
 *
 * @param tokenId                             注册令牌主键
 * @param paymentRecordId                     线上付款记录主键
 * @param outTradeNo                          商户订单号
 * @param openid                              支付者 openid（仅内存传递，用于绑定外部身份）
 * @param phoneToken                          下单时记录的手机号比对令牌；V11 之前的订单为 null
 * @param creditMinor                         计入 SVIP 累计额度的金额（分）
 * @param presentedAuthorizationDocumentId    付款前展示的授权书主键
 */
public record PaidRegistrationOrder(
        long tokenId,
        long paymentRecordId,
        String outTradeNo,
        String openid,
        String phoneToken,
        long creditMinor,
        long presentedAuthorizationDocumentId) {
}
