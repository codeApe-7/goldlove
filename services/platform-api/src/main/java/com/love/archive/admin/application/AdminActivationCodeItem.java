package com.love.archive.admin.application;

import java.time.OffsetDateTime;

/**
 * @param boundPhoneRegistered 绑定手机号当前是否已有账号。注册不做短信验证，
 *                             所以这一列提醒管理员：号还没注册时，抢注的人就能领走这个码。
 */
public record AdminActivationCodeItem(
        long id,
        String code,
        String boundPhone,
        boolean boundPhoneRegistered,
        String grantedTier,
        String status,
        String note,
        OffsetDateTime createdAt,
        OffsetDateTime redeemedAt,
        String redeemedPhone) {
}
