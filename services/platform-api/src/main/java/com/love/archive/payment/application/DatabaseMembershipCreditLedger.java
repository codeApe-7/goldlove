package com.love.archive.payment.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.payment.persistence.PaymentRecordEntity;
import com.love.archive.payment.persistence.PaymentRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class DatabaseMembershipCreditLedger implements MembershipCreditLedger {

    private final PaymentRecordMapper paymentRecordMapper;

    @Override
    public boolean claimCredit(long paymentRecordId, long creditMinor) {
        return paymentRecordMapper.update(
                Wrappers.<PaymentRecordEntity>lambdaUpdate()
                        .eq(PaymentRecordEntity::getId, paymentRecordId)
                        .eq(PaymentRecordEntity::getMembershipCreditMinor, 0L)
                        .set(PaymentRecordEntity::getMembershipCreditMinor, creditMinor)) == 1;
    }
}
