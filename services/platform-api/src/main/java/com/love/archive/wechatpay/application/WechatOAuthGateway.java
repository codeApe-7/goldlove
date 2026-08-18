package com.love.archive.wechatpay.application;

/**
 * 公众号网页授权：用授权码换取支付者标识，并给出服务端固定回跳地址的授权链接。
 * 回跳地址来自服务端配置，不接受调用方传入，避免开放重定向。
 */
public interface WechatOAuthGateway {

    String appId();

    String authorizeUrl(String state);

    String resolveOpenId(String authorizationCode);
}
