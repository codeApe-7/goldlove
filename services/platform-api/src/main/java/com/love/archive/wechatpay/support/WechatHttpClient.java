package com.love.archive.wechatpay.support;

import java.util.Map;

/** 渠道出网调用抽象，便于在测试中替换真实 HTTP 往返。 */
public interface WechatHttpClient {

    HttpTextResponse send(String httpMethod, String url, Map<String, String> headers, String body);

    record HttpTextResponse(int statusCode, String body) {
    }
}
