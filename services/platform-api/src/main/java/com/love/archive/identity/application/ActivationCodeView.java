package com.love.archive.identity.application;

import com.love.archive.identity.domain.ActivationCodeStatus;
import com.love.archive.identity.domain.MembershipTier;
import com.love.archive.identity.persistence.ActivationCodeEntity;
import java.time.OffsetDateTime;

/**
 * @param boundPhoneRegistered 该手机号当前是否已注册。生成时提示管理员，
 *                             因为注册不做短信验证，抢注同号会把码领走。
 */
public record ActivationCodeView(
        Long id,
        String code,
        String boundPhone,
        boolean boundPhoneRegistered,
        MembershipTier grantedTier,
        ActivationCodeStatus status,
        String note,
        OffsetDateTime createdAt,
        OffsetDateTime redeemedAt) {

    public static ActivationCodeView of(ActivationCodeEntity entity, boolean boundPhoneRegistered) {
        return new ActivationCodeView(
                entity.getId(),
                entity.getCode(),
                entity.getBoundPhone(),
                boundPhoneRegistered,
                entity.getGrantedTier(),
                entity.getStatus(),
                entity.getNote(),
                entity.getCreatedAt(),
                entity.getRedeemedAt());
    }
}
