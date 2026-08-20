package com.love.archive.admin.persistence.query;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminPaymentOrderRow {

    private Long id;
    private String outTradeNo;
    private String channel;
    private Long amountMinor;
    private String status;
    private OffsetDateTime paidAt;
    private OffsetDateTime createdAt;
    private Long userAccountId;
    private String phone;
}
