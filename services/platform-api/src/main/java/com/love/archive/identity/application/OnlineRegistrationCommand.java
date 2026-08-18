package com.love.archive.identity.application;

/**
 * 线上注册命令。
 *
 * @param registrationToken 支付成功后签发的一次性注册令牌
 * @param phone             手机号，登录用
 * @param password          正式密码
 * @param requestId         请求标识，用于审计
 */
public record OnlineRegistrationCommand(
        String registrationToken,
        String phone,
        String password,
        String requestId) {
}
