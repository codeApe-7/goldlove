package com.love.archive.identity.application;

import com.love.archive.payment.application.MembershipGrantPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * {@link MembershipGrantPort} 的 identity 侧实现：支付结算成功后把额度计入账号并升级等级。
 * 端口声明在 payment 是为了避免 payment → identity 的模块环。
 */
@Service
@RequiredArgsConstructor
class DatabaseMembershipGrantPort implements MembershipGrantPort {

    private final MembershipService membershipService;

    @Override
    public void grantPaidMembership(long accountId, long paymentRecordId, long creditMinor) {
        membershipService.creditPayment(accountId, paymentRecordId, creditMinor);
    }
}
