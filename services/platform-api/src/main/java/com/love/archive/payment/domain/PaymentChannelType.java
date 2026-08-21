package com.love.archive.payment.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum PaymentChannelType {
    XPAY_ALIPAY("XPAY_ALIPAY");

    @EnumValue
    private final String databaseValue;

    PaymentChannelType(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }
}
