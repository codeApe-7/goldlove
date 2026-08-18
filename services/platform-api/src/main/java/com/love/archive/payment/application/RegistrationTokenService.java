package com.love.archive.payment.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.common.web.ApiException;
import com.love.archive.payment.config.OnlinePaymentProperties;
import com.love.archive.payment.domain.RegistrationTokenStatus;
import com.love.archive.payment.domain.WechatOrderStatus;
import com.love.archive.payment.persistence.PaymentRecordEntity;
import com.love.archive.payment.persistence.RegistrationTokenEntity;
import com.love.archive.payment.persistence.RegistrationTokenMapper;
import com.love.archive.payment.persistence.WechatPaymentOrderEntity;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 一次性注册令牌：支付成功后签发，注册时消费。库里只存令牌 HMAC，明文只在签发响应里出现一次。
 */
@Service
@RequiredArgsConstructor
public class RegistrationTokenService {

    private static final int TOKEN_RANDOM_BYTES = 32;

    private final RegistrationTokenMapper registrationTokenMapper;
    private final WechatPaymentOrderStore orderStore;
    private final OnlinePaymentProperties properties;
    private final SecureRandom secureRandom;

    /**
     * 为已支付且未注册的订单签发注册令牌；重新签发会作废该订单此前的待用令牌。
     */
    @Transactional
    public IssuedRegistrationToken issue(String outTradeNo) {
        WechatPaymentOrderEntity order = orderStore.lockByOutTradeNo(outTradeNo);
        if (order.getStatus() != WechatOrderStatus.PAID) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "REGISTRATION_ORDER_NOT_PAID", "订单尚未支付成功");
        }
        PaymentRecordEntity payment = orderStore.requirePaymentRecord(order.getPaymentRecordId());
        if (Boolean.TRUE.equals(payment.getRegistered())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "REGISTRATION_ALREADY_COMPLETED", "该订单已完成注册");
        }

        OffsetDateTime now = OffsetDateTime.now();
        registrationTokenMapper.update(Wrappers.<RegistrationTokenEntity>lambdaUpdate()
                .eq(RegistrationTokenEntity::getWechatPaymentOrderId, order.getId())
                .eq(RegistrationTokenEntity::getStatus, RegistrationTokenStatus.UNUSED)
                .set(RegistrationTokenEntity::getStatus, RegistrationTokenStatus.EXPIRED));

        String rawToken = generateToken();
        OffsetDateTime expiresAt = now.plus(properties.getRegistrationTokenTtl());
        RegistrationTokenEntity token = new RegistrationTokenEntity();
        token.setTokenHmac(orderStore.hmacToken(rawToken));
        token.setWechatPaymentOrderId(order.getId());
        token.setOutTradeNo(order.getOutTradeNo());
        token.setOpenidCiphertext(order.getOpenidCiphertext());
        token.setOpenidHmac(order.getOpenidHmac());
        token.setStatus(RegistrationTokenStatus.UNUSED);
        token.setExpiresAt(expiresAt);
        token.setCreatedAt(now);
        registrationTokenMapper.insert(token);
        return new IssuedRegistrationToken(rawToken, expiresAt);
    }

    /**
     * 校验令牌并锁定其关联的已支付订单，供调用方在同一事务内完成建档。
     * 必须由 identity 侧的注册事务调用，随后配对 {@link #completeRegistration}。
     */
    @Transactional
    public PaidRegistrationOrder lockPaidOrder(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }
        RegistrationTokenEntity token = registrationTokenMapper.selectOne(
                Wrappers.<RegistrationTokenEntity>lambdaQuery()
                        .eq(RegistrationTokenEntity::getTokenHmac, orderStore.hmacToken(rawToken))
                        .last("FOR UPDATE"));
        if (token == null) {
            throw invalidToken();
        }
        if (token.getStatus() == RegistrationTokenStatus.USED) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "REGISTRATION_TOKEN_USED", "注册令牌已被使用");
        }
        if (token.getStatus() == RegistrationTokenStatus.EXPIRED
                || !token.getExpiresAt().isAfter(OffsetDateTime.now())) {
            throw new ApiException(
                    HttpStatus.GONE, "REGISTRATION_TOKEN_EXPIRED", "注册令牌已过期，请重新获取");
        }

        WechatPaymentOrderEntity order = orderStore.lockByOutTradeNo(token.getOutTradeNo());
        if (order.getStatus() != WechatOrderStatus.PAID) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "REGISTRATION_ORDER_NOT_PAID", "订单尚未支付成功");
        }
        PaymentRecordEntity payment = orderStore.requirePaymentRecord(order.getPaymentRecordId());
        if (Boolean.TRUE.equals(payment.getRegistered())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "REGISTRATION_ALREADY_COMPLETED", "该订单已完成注册");
        }

        long creditMinor = payment.getPaidAmountMinor() == null
                ? payment.getAmountMinor()
                : payment.getPaidAmountMinor();
        return new PaidRegistrationOrder(
                token.getId(),
                payment.getId(),
                order.getOutTradeNo(),
                orderStore.decryptOpenid(token.getOpenidCiphertext()),
                creditMinor,
                order.getPresentedAuthorizationDocumentId());
    }

    /**
     * 把令牌置为已使用并把付款记录绑定到新账号。必须与 {@link #lockPaidOrder} 处于同一事务。
     */
    @Transactional
    public void completeRegistration(long tokenId, long paymentRecordId, long userAccountId) {
        OffsetDateTime now = OffsetDateTime.now();
        int tokenUpdated = registrationTokenMapper.update(Wrappers.<RegistrationTokenEntity>lambdaUpdate()
                .eq(RegistrationTokenEntity::getId, tokenId)
                .eq(RegistrationTokenEntity::getStatus, RegistrationTokenStatus.UNUSED)
                .set(RegistrationTokenEntity::getStatus, RegistrationTokenStatus.USED)
                .set(RegistrationTokenEntity::getUsedAt, now)
                .set(RegistrationTokenEntity::getUserAccountId, userAccountId));
        if (tokenUpdated != 1) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "REGISTRATION_TOKEN_USED", "注册令牌已被使用");
        }
        if (orderStore.bindRegisteredAccount(paymentRecordId, userAccountId) != 1) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "REGISTRATION_ALREADY_COMPLETED", "该订单已完成注册");
        }
    }

    private String generateToken() {
        byte[] random = new byte[TOKEN_RANDOM_BYTES];
        secureRandom.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private static ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "REGISTRATION_TOKEN_INVALID", "注册令牌无效");
    }
}
