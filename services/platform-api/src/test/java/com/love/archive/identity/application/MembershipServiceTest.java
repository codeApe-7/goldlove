package com.love.archive.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.domain.MembershipTier;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.payment.domain.PaymentChannelType;
import com.love.archive.payment.domain.PaymentStatus;
import com.love.archive.payment.persistence.PaymentRecordEntity;
import com.love.archive.payment.persistence.PaymentRecordMapper;
import com.love.archive.testsupport.ApiIntegrationTest;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.membership.svip-threshold-minor=59900")
class MembershipServiceTest extends ApiIntegrationTest {

    @Autowired private MembershipService membershipService;
    @Autowired private UserAccountMapper userAccountMapper;
    @Autowired private PaymentRecordMapper paymentRecordMapper;

    @BeforeEach
    void cleanState() {
        resetDatabase();
    }

    @Test
    void newAccountsStartAsFreeWithoutCredit() {
        long accountId = insertAccount("13800138000");

        MembershipView view = membershipService.current(accountId);

        assertThat(view.tier()).isEqualTo(MembershipTier.FREE);
        assertThat(view.creditMinor()).isZero();
        assertThat(view.svipThresholdMinor()).isEqualTo(59_900L);
        assertThat(view.creditToNextTierMinor()).isEqualTo(59_900L);
    }

    @Test
    void anyPaymentLiftsFreeToVip() {
        long accountId = insertAccount("13800138001");
        long paymentId = recordPaid(accountId, "PAY-BELOW", 19_900L);

        MembershipView view = membershipService.creditPayment(accountId, paymentId, 19_900L);

        assertThat(view.tier()).isEqualTo(MembershipTier.VIP);
        assertThat(view.creditMinor()).isEqualTo(19_900L);
        assertThat(view.creditToNextTierMinor()).isEqualTo(40_000L);
        assertThat(reloadTier(accountId)).isEqualTo(MembershipTier.VIP);
    }

    @Test
    void upgradesToSvipOnceCumulativeCreditReachesThreshold() {
        long accountId = insertAccount("13800138002");
        long first = recordPaid(accountId, "PAY-CUMULATIVE-1", 29_900L);
        long second = recordPaid(accountId, "PAY-CUMULATIVE-2", 30_000L);

        assertThat(membershipService.creditPayment(accountId, first, 29_900L).tier())
                .isEqualTo(MembershipTier.VIP);
        MembershipView upgraded = membershipService.creditPayment(accountId, second, 30_000L);

        assertThat(upgraded.tier()).isEqualTo(MembershipTier.SVIP);
        assertThat(upgraded.creditMinor()).isEqualTo(59_900L);
        assertThat(upgraded.creditToNextTierMinor()).isZero();
        assertThat(reloadTier(accountId)).isEqualTo(MembershipTier.SVIP);
    }

    @Test
    void creditingTheSamePaymentTwiceIsIdempotent() {
        long accountId = insertAccount("13800138003");
        long paymentId = recordPaid(accountId, "PAY-IDEMPOTENT", 59_900L);

        MembershipView first = membershipService.creditPayment(accountId, paymentId, 59_900L);
        MembershipView second = membershipService.creditPayment(accountId, paymentId, 59_900L);

        assertThat(first.tier()).isEqualTo(MembershipTier.SVIP);
        assertThat(second.tier()).isEqualTo(MembershipTier.SVIP);
        assertThat(second.creditMinor()).isEqualTo(59_900L);
        assertThat(reloadCredit(accountId)).isEqualTo(59_900L);
        assertThat(paymentRecordMapper.selectById(paymentId).getMembershipCreditMinor())
                .isEqualTo(59_900L);
    }

    @Test
    void grantTierUpgradesWithoutTouchingPaidCredit() {
        long accountId = insertAccount("13800138004");

        MembershipView view = membershipService.grantTier(accountId, MembershipTier.VIP);

        assertThat(view.tier()).isEqualTo(MembershipTier.VIP);
        // 兑码不是付费，绝不能顶 SVIP 的累计阈值。
        assertThat(view.creditMinor()).isZero();
        assertThat(reloadCredit(accountId)).isZero();
    }

    @Test
    void grantTierNeverDowngrades() {
        long accountId = insertAccount("13800138005");
        membershipService.grantTier(accountId, MembershipTier.SVIP);

        MembershipView view = membershipService.grantTier(accountId, MembershipTier.VIP);

        assertThat(view.tier()).isEqualTo(MembershipTier.SVIP);
        assertThat(reloadTier(accountId)).isEqualTo(MembershipTier.SVIP);
    }

    @Test
    void alignsAccountsWhoseCreditAlreadyReachesTheThreshold() {
        long accountId = insertAccount("13800138006");
        userAccountMapper.update(null, Wrappers.<UserAccountEntity>lambdaUpdate()
                .eq(UserAccountEntity::getId, accountId)
                .set(UserAccountEntity::getMembershipCreditMinor, 80_000L)
                .set(UserAccountEntity::getMembershipTier, MembershipTier.FREE));

        MembershipView view = membershipService.current(accountId);

        assertThat(view.tier()).isEqualTo(MembershipTier.SVIP);
        assertThat(reloadTier(accountId)).isEqualTo(MembershipTier.SVIP);
    }

    private long insertAccount(String phone) {
        OffsetDateTime now = OffsetDateTime.now();
        UserAccountEntity account = new UserAccountEntity();
        account.setPhone(phone);
        account.setPasswordHash("$argon2id$test-placeholder");
        account.setStatus(AccountStatus.ACTIVE);
        account.setMembershipTier(MembershipTier.FREE);
        account.setMembershipCreditMinor(0L);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        userAccountMapper.insert(account);
        return account.getId();
    }

    private long recordPaid(long accountId, String outTradeNo, long amountMinor) {
        OffsetDateTime now = OffsetDateTime.now();
        PaymentRecordEntity payment = new PaymentRecordEntity();
        payment.setUserAccountId(accountId);
        payment.setOutTradeNo(outTradeNo);
        payment.setTransactionId("TXN-" + outTradeNo);
        payment.setChannel(PaymentChannelType.XPAY_ALIPAY);
        payment.setAmountMinor(amountMinor);
        payment.setCurrency("CNY");
        payment.setStatus(PaymentStatus.PAID);
        payment.setMembershipCreditMinor(0L);
        payment.setPaidAt(now.minusMinutes(1));
        payment.setCreatedAt(now);
        paymentRecordMapper.insert(payment);
        return payment.getId();
    }

    private MembershipTier reloadTier(long accountId) {
        return userAccountMapper.selectById(accountId).getMembershipTier();
    }

    private long reloadCredit(long accountId) {
        return userAccountMapper.selectById(accountId).getMembershipCreditMinor();
    }
}
