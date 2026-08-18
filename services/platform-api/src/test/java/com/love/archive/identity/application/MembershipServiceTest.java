package com.love.archive.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.love.archive.admin.domain.AdminStatus;
import com.love.archive.admin.persistence.AdminUserEntity;
import com.love.archive.admin.persistence.AdminUserMapper;
import com.love.archive.identity.domain.AccountStatus;
import com.love.archive.identity.domain.MembershipTier;
import com.love.archive.identity.domain.RegistrationChannel;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.payment.application.PaidPayment;
import com.love.archive.payment.application.PaymentRecorder;
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
    @Autowired private PaymentRecorder paymentRecorder;
    @Autowired private UserAccountMapper userAccountMapper;
    @Autowired private PaymentRecordMapper paymentRecordMapper;
    @Autowired private AdminUserMapper adminUserMapper;

    private long adminId;

    @BeforeEach
    void cleanState() {
        resetDatabase();
        OffsetDateTime now = OffsetDateTime.now();
        AdminUserEntity admin = new AdminUserEntity();
        admin.setUsername("membership-admin");
        admin.setDisplayName("Membership Admin");
        admin.setPasswordHash("$argon2id$test-placeholder");
        admin.setStatus(AdminStatus.ACTIVE);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        adminUserMapper.insert(admin);
        adminId = admin.getId();
    }

    @Test
    void newAccountsStartAsVipWithoutCredit() {
        long accountId = insertAccount("membership-new");

        MembershipView view = membershipService.current(accountId);

        assertThat(view.tier()).isEqualTo(MembershipTier.VIP);
        assertThat(view.creditMinor()).isZero();
        assertThat(view.svipThresholdMinor()).isEqualTo(59_900L);
        assertThat(view.creditToNextTierMinor()).isEqualTo(59_900L);
    }

    @Test
    void keepsVipBelowThreshold() {
        long accountId = insertAccount("membership-below");
        long paymentId = recordPaid(accountId, "PAY-BELOW", 19_900L);

        MembershipView view = membershipService.creditPayment(accountId, paymentId, 19_900L);

        assertThat(view.tier()).isEqualTo(MembershipTier.VIP);
        assertThat(view.creditMinor()).isEqualTo(19_900L);
        assertThat(view.creditToNextTierMinor()).isEqualTo(40_000L);
        assertThat(reloadTier(accountId)).isEqualTo(MembershipTier.VIP);
    }

    @Test
    void upgradesToSvipOnceCumulativeCreditReachesThreshold() {
        long accountId = insertAccount("membership-cumulative");
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
        long accountId = insertAccount("membership-idempotent");
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
    void alignsHistoricalAccountsWhoseCreditAlreadyReachesTheThreshold() {
        long accountId = insertAccount("membership-backfilled");
        userAccountMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers
                .<UserAccountEntity>lambdaUpdate()
                .eq(UserAccountEntity::getId, accountId)
                .set(UserAccountEntity::getMembershipCreditMinor, 80_000L)
                .set(UserAccountEntity::getMembershipTier, MembershipTier.VIP));

        MembershipView view = membershipService.current(accountId);

        assertThat(view.tier()).isEqualTo(MembershipTier.SVIP);
        assertThat(reloadTier(accountId)).isEqualTo(MembershipTier.SVIP);
    }

    @Test
    void manualProvisioningCreditIsRecordedOnThePaymentRecord() {
        long accountId = insertAccount("membership-manual-anchor");
        long paymentId = recordPaid(accountId, "PAY-MANUAL-ANCHOR", 19_900L);

        membershipService.creditPayment(accountId, paymentId, 19_900L);

        PaymentRecordEntity payment = paymentRecordMapper.selectById(paymentId);
        assertThat(payment.getMembershipCreditMinor()).isEqualTo(19_900L);
        assertThat(payment.getRegistered()).isFalse();
    }

    private long insertAccount(String phoneHmac) {
        OffsetDateTime now = OffsetDateTime.now();
        UserAccountEntity account = new UserAccountEntity();
        account.setPhoneCiphertext(new byte[] {1, 2, 3});
        account.setPhoneHmac(phoneHmac);
        account.setStatus(AccountStatus.PAID_PENDING_ACTIVATION);
        account.setCreatedByAdminId(adminId);
        account.setRegistrationChannel(RegistrationChannel.ADMIN_MANUAL);
        account.setMembershipTier(MembershipTier.VIP);
        account.setMembershipCreditMinor(0L);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        userAccountMapper.insert(account);
        return account.getId();
    }

    private long recordPaid(long accountId, String reference, long amountMinor) {
        return paymentRecorder.recordPaid(new PaidPayment(
                accountId,
                reference,
                amountMinor,
                OffsetDateTime.now().minusMinutes(1),
                adminId,
                null,
                "线下付款",
                OffsetDateTime.now()));
    }

    private MembershipTier reloadTier(long accountId) {
        return userAccountMapper.selectById(accountId).getMembershipTier();
    }

    private long reloadCredit(long accountId) {
        return userAccountMapper.selectById(accountId).getMembershipCreditMinor();
    }
}
