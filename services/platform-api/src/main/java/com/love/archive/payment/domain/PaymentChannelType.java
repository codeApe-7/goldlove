package com.love.archive.payment.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum PaymentChannelType {
    MANUAL("MANUAL"),
    WECHAT_JSAPI("WECHAT_JSAPI"),
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
