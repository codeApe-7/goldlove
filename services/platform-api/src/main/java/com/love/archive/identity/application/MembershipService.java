package com.love.archive.identity.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.common.web.ApiException;
import com.love.archive.identity.config.MembershipProperties;
import com.love.archive.identity.domain.MembershipTier;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import com.love.archive.payment.application.MembershipCreditLedger;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会员等级：注册即 FREE，付费或兑换激活码升 VIP，累计付费额度达阈值自动升 SVIP。
 * 等级只升不降，幂等锚点是付款记录上的 membership_credit_minor。
 */
@Service
@RequiredArgsConstructor
public class MembershipService {

    private final UserAccountMapper userAccountMapper;
    private final MembershipCreditLedger membershipCreditLedger;
    private final MembershipProperties properties;

    /**
     * 把一笔付款计入累计额度并按需升级；同一笔付款重复调用不会重复累加。
     */
    @Transactional
    public MembershipView creditPayment(long accountId, long paymentRecordId, long creditMinor) {
        if (creditMinor < 0) {
            throw new IllegalArgumentException("会员额度不能为负数");
        }
        UserAccountEntity account = lockAccount(accountId);
        if (creditMinor == 0 || !membershipCreditLedger.claimCredit(paymentRecordId, creditMinor)) {
            return align(account);
        }
        long credited = currentCredit(account) + creditMinor;
        return apply(account, credited, resolveTier(account, credited));
    }

    /**
     * 直接授予等级，供激活码兑换使用。不动累计付费额度——兑码不是付费，
     * 不应该顶 SVIP 的付费阈值。
     */
    @Transactional
    public MembershipView grantTier(long accountId, MembershipTier tier) {
        UserAccountEntity account = lockAccount(accountId);
        long credited = currentCredit(account);
        MembershipTier target = higher(currentTier(account), tier);
        if (target == currentTier(account)) {
            return toView(target, credited);
        }
        return apply(account, credited, target);
    }

    /**
     * 读取当前会员状态，并顺带把「额度已达标但等级未跟上」的数据对齐。
     */
    @Transactional
    public MembershipView current(long accountId) {
        return align(lockAccount(accountId));
    }

    private MembershipView align(UserAccountEntity account) {
        long credited = currentCredit(account);
        MembershipTier tier = resolveTier(account, credited);
        if (tier == currentTier(account)) {
            return toView(tier, credited);
        }
        return apply(account, credited, tier);
    }

    private MembershipView apply(UserAccountEntity account, long credited, MembershipTier tier) {
        OffsetDateTime now = OffsetDateTime.now();
        int updated = userAccountMapper.update(
                Wrappers.<UserAccountEntity>lambdaUpdate()
                        .eq(UserAccountEntity::getId, account.getId())
                        .set(UserAccountEntity::getMembershipCreditMinor, credited)
                        .set(UserAccountEntity::getMembershipTier, tier)
                        .set(UserAccountEntity::getUpdatedAt, now));
        if (updated != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "MEMBERSHIP_UPDATE_CONFLICT", "会员状态已变化，请重试");
        }
        return toView(tier, credited);
    }

    /** 付过钱至少是 VIP，累计达阈值是 SVIP；已有等级只升不降。 */
    private MembershipTier resolveTier(UserAccountEntity account, long credited) {
        MembershipTier earned;
        if (credited >= properties.getSvipThresholdMinor()) {
            earned = MembershipTier.SVIP;
        } else if (credited > 0) {
            earned = MembershipTier.VIP;
        } else {
            earned = MembershipTier.FREE;
        }
        return higher(currentTier(account), earned);
    }

    private MembershipView toView(MembershipTier tier, long credited) {
        long threshold = properties.getSvipThresholdMinor();
        long remaining = tier == MembershipTier.SVIP ? 0L : Math.max(0L, threshold - credited);
        return new MembershipView(tier, credited, threshold, remaining);
    }

    private UserAccountEntity lockAccount(long accountId) {
        UserAccountEntity account = userAccountMapper.selectOne(
                Wrappers.<UserAccountEntity>lambdaQuery()
                        .eq(UserAccountEntity::getId, accountId)
                        .last("FOR UPDATE"));
        if (account == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "账号不存在");
        }
        return account;
    }

    /** 枚举声明顺序即等级高低：FREE < VIP < SVIP。 */
    private static MembershipTier higher(MembershipTier left, MembershipTier right) {
        return left.compareTo(right) >= 0 ? left : right;
    }

    private static long currentCredit(UserAccountEntity account) {
        return account.getMembershipCreditMinor() == null ? 0L : account.getMembershipCreditMinor();
    }

    private static MembershipTier currentTier(UserAccountEntity account) {
        return account.getMembershipTier() == null ? MembershipTier.FREE : account.getMembershipTier();
    }
}
