package com.love.archive.payment.application;

import com.love.archive.payment.domain.PaymentStatus;
import com.love.archive.payment.persistence.PaymentRecordEntity;
import com.love.archive.payment.persistence.PaymentRecordMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
class DatabasePaymentRecorder implements PaymentRecorder {

    private final PaymentRecordMapper paymentRecordMapper;

    DatabasePaymentRecorder(PaymentRecordMapper paymentRecordMapper) {
        this.paymentRecordMapper = paymentRecordMapper;
    }

    @Override
    public void recordPaid(PaidPayment command) {
        PaymentRecordEntity payment = new PaymentRecordEntity();
        payment.setUserAccountId(command.userAccountId());
        payment.setPaymentReference(command.paymentReference());
        payment.setAmountMinor(command.amountMinor());
        payment.setCurrency("CNY");
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(command.paidAt());
        payment.setOperatorAdminId(command.operatorAdminId());
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
