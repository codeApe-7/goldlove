package com.love.archive.payment.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.love.archive.payment.domain.PaymentChannelType;
import com.love.archive.payment.domain.PaymentOrderStatus;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("payment_order")
public class PaymentOrderEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String outTradeNo;
    private Long userAccountId;
    private PaymentChannelType channel;
    private Long amountMinor;
    private PaymentOrderStatus status;
    private String transactionId;
    private OffsetDateTime paidAt;
    private Long paymentRecordId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Version
    private Long version;
}
