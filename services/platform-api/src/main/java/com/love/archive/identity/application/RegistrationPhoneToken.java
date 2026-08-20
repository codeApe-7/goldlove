package com.love.archive.identity.application;

import com.love.archive.common.security.SensitiveValueProtector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 手机号的不可逆比对令牌。订单侧只存该令牌，注册时用同一算法重算比对，
 * 因此签发与校验必须共用这一处实现。
 */
@Component
@RequiredArgsConstructor
class RegistrationPhoneToken {

    private static final String DOMAIN = "registration:phone";

    private final SensitiveValueProtector protector;

    String of(String normalizedPhone) {
        return protector.hmac(DOMAIN, normalizedPhone);
    }
}
