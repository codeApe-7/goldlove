package com.love.archive.xpay.support;

import java.util.Map;

/** 易支付出网调用抽象，便于在测试中替换真实 HTTP 往返。 */
public interface XpayHttpClient {

    HttpTextResponse postForm(String url, Map<String, String> form);

    record HttpTextResponse(int statusCode, String body, Map<String, String> headers) {
    }
}
