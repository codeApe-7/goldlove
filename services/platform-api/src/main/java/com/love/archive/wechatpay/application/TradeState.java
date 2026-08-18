package com.love.archive.wechatpay.application;

public enum TradeState {
    SUCCESS,
    REFUND,
    NOTPAY,
    CLOSED,
    REVOKED,
    USERPAYING,
    PAYERROR,
    UNKNOWN;

    public static TradeState from(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        try {
            return valueOf(raw.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return UNKNOWN;
        }
    }
}
