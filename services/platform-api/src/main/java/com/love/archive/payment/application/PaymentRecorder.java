package com.love.archive.payment.application;

public interface PaymentRecorder {

    /**
     * @return 新建付款记录的主键，供会员额度记账使用
     */
    long recordPaid(PaidPayment payment);
}
