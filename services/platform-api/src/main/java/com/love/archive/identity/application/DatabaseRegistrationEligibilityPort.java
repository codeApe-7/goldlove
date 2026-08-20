package com.love.archive.identity.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.common.web.ApiException;
import com.love.archive.identity.domain.PhoneNormalizer;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.identity.security.AuthenticationAttemptLimiter;
import com.love.archive.identity.security.PhoneProtector;
import com.love.archive.payment.application.RegistrationEligibilityPort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 线上下单前的注册资格预检。把手机号冲突挡在付款之前，避免出现「已付款但无法注册、
 * 又没有退款路径」的卡死订单。
 */
@Service
@RequiredArgsConstructor
class DatabaseRegistrationEligibilityPort implements RegistrationEligibilityPort {

    private static final String RATE_LIMIT_FLOW = "guest-online-order";

    private final UserAccountMapper userAccountMapper;
    private final PhoneNormalizer phoneNormalizer;
    private final PhoneProtector phoneProtector;
    private final RegistrationPhoneToken phoneToken;
    private final AuthenticationAttemptLimiter attemptLimiter;

    @Override
    public String requireRegistrablePhone(String rawPhone, String clientAddress) {
        String phone = normalize(rawPhone);
        // 先限流再查库：这个端点会暴露「该手机号是否已注册」，不限流就是免费的探测口子。
        // 成功后刻意不 resetAccount —— 探测命中的多是未注册号码，一重置等于放开无限探测。
        attemptLimiter.checkAndConsume(RATE_LIMIT_FLOW, phone, clientAddress);
        if (userAccountMapper.selectCount(Wrappers.<UserAccountEntity>lambdaQuery()
                .eq(UserAccountEntity::getPhoneHmac, phoneProtector.searchHash(phone))) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_ALREADY_EXISTS", "该手机号已存在账号");
        }
        return phoneToken.of(phone);
    }

    private String normalize(String rawPhone) {
        try {
            return phoneNormalizer.normalize(rawPhone);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHONE_INVALID", "手机号格式不正确");
        }
    }
}
