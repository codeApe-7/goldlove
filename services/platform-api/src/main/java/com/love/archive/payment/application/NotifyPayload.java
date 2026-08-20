package com.love.archive.payment.application;

import java.util.Map;

/**
 * 渠道回调的原始载体，由各渠道自行解析验签。
 * 微信回调走请求头 + 原始 body；易支付回调走 form/query 参数。
 *
 * @param headers 原始请求头
 * @param params  form / query 参数
 * @param body    未经加工的原始请求体（可能为空）
 */
public record NotifyPayload(
        Map<String, String> headers,
        Map<String, String> params,
        String body) {
}
