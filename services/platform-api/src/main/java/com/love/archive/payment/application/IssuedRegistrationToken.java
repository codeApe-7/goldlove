package com.love.archive.payment.application;

import java.time.OffsetDateTime;

/** 一次性注册令牌，明文只在签发响应中返回一次，库里只存 HMAC。 */
public record IssuedRegistrationToken(String token, OffsetDateTime expiresAt) {
}
