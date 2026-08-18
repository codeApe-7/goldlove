package com.love.archive.payment.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.love.archive.payment.domain.PaymentChannelType;
import com.love.archive.payment.domain.PaymentStatus;
import com.love.archive.payment.persistence.PaymentRecordEntity;
import com.love.archive.payment.persistence.PaymentRecordMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class DatabasePaymentRecorder implements PaymentRecorder, PaymentAuthorizationEvidence, MembershipCreditLedger {

    private final PaymentRecordMapper paymentRecordMapper;

    @Override
    public long recordPaid(PaidPayment command) {
        PaymentRecordEntity payment = new PaymentRecordEntity();
        payment.setUserAccountId(command.userAccountId());
        payment.setPaymentReference(command.paymentReference());
        payment.setAmountMinor(command.amountMinor());
        payment.setCurrency("CNY");
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaymentChannel(PaymentChannelType.MANUAL);
        payment.setMembershipCreditMinor(0L);
        payment.setRegistered(false);
        payment.setPaidAt(command.paidAt());
        payment.setOperatorAdminId(command.operatorAdminId());
        payment.setPresentedAuthorizationDocumentId(command.presentedAuthorizationDocumentId());
        payment.setNote(command.note());
        payment.setCreatedAt(command.createdAt());
        try {
            paymentRecordMapper.insert(payment);
        } catch (DataIntegrityViolationException exception) {
            if (containsConstraint(exception, "uq_payment_record_reference")) {
                throw new PaymentReferenceConflictException(exception);
            }
            throw exception;
        }
        return payment.getId();
    }

    @Override
    public boolean claimCredit(long paymentRecordId, long creditMinor) {
        return paymentRecordMapper.update(
                Wrappers.<PaymentRecordEntity>lambdaUpdate()
                        .eq(PaymentRecordEntity::getId, paymentRecordId)
                        .eq(PaymentRecordEntity::getMembershipCreditMinor, 0L)
                        .set(PaymentRecordEntity::getMembershipCreditMinor, creditMinor)) == 1;
    }

    @Override
    public Optional<PresentedAuthorization> findPaidAuthorization(long accountId) {
        PaymentRecordEntity payment = paymentRecordMapper.selectOne(
                Wrappers.<PaymentRecordEntity>lambdaQuery()
                        .eq(PaymentRecordEntity::getUserAccountId, accountId)
                        .eq(PaymentRecordEntity::getStatus, PaymentStatus.PAID)
                        .orderByDesc(PaymentRecordEntity::getPaidAt)
                        .orderByDesc(PaymentRecordEntity::getId)
                        .last("LIMIT 1"));
        if (payment == null || payment.getPresentedAuthorizationDocumentId() == null) {
            return Optional.empty();
        }
        return Optional.of(new PresentedAuthorization(payment.getId(), payment.getPresentedAuthorizationDocumentId()));
    }

    private static boolean containsConstraint(Throwable exception, String constraintName) {
        Throwable current = exception;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(constraintName)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
