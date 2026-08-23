package com.love.archive.identity.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.common.security.GuestMembershipAccess;
import com.love.archive.identity.domain.MembershipTier;
import com.love.archive.identity.persistence.UserAccountEntity;
import com.love.archive.identity.persistence.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link GuestMembershipAccess} 的实现。
 *
 * <p>刻意**不复用** {@link MembershipService#current(long)}：那个方法是写路径，
 * 带 {@code SELECT ... FOR UPDATE} 并且会顺手把「额度已达标但等级未跟上」的数据对齐。
 * 付费内容的门禁是高频只读路径，用它等于每次浏览都在 {@code user_account} 上打一把行锁。
 * 这里只读一列，不加锁、不写回。</p>
 *
 * <p>代价是：如果某账号的额度已经够 SVIP 但等级列还没被对齐过，这里看到的是旧等级。
 * 对门禁来说无害——那种情况下等级至少已经是 VIP（额度是付款累计出来的），
 * 而这个端口只区分「付费 / 未付费」。</p>
 */
@Service
@RequiredArgsConstructor
public class DatabaseGuestMembershipAccess implements GuestMembershipAccess {

    private final UserAccountMapper userAccountMapper;

    @Override
    @Transactional(readOnly = true)
    public boolean isPaidMember(long accountId) {
        UserAccountEntity account = userAccountMapper.selectOne(
                Wrappers.<UserAccountEntity>lambdaQuery()
                        .select(UserAccountEntity::getMembershipTier)
                        .eq(UserAccountEntity::getId, accountId));
        if (account == null || account.getMembershipTier() == null) {
            return false;
        }
        // 用「不是 FREE」而不是「是 VIP 或 SVIP」：以后在 SVIP 之上再加一档，
        // 这里不用跟着改，而漏改会直接把付费用户挡在门外。
        return account.getMembershipTier() != MembershipTier.FREE;
    }
}
