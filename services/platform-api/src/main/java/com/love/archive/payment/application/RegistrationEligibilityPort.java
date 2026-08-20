package com.love.archive.payment.application;

/**
 * 线上注册资格出站端口。由 identity 模块实现，避免 payment 反向依赖 identity 造成模块环
 * （identity 已依赖 payment::application）。
 *
 * <p>手机号的规范化、占用校验与探测限流全部留在 identity 侧；payment 只拿到一个不可逆的
 * 比对令牌存进订单，永远看不到手机号明文或密文。</p>
 */
public interface RegistrationEligibilityPort {

    /**
     * 校验手机号格式与「尚未注册」，返回该手机号的不可逆比对令牌。
     *
     * @param rawPhone      用户提交的原始手机号
     * @param clientAddress 调用方地址，用于探测限流
     * @return 该手机号的比对令牌，供订单落库与注册时校验一致
     * @throws com.love.archive.common.web.ApiException 格式非法 PHONE_INVALID；
     *                                                 已有账号 ACCOUNT_ALREADY_EXISTS；
     *                                                 探测过频 AUTH_RATE_LIMITED
     */
    String requireRegistrablePhone(String rawPhone, String clientAddress);
}
