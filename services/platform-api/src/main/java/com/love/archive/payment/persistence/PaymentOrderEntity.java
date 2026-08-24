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
    /** 渠道侧订单号（易支付 trade_no），下单成功即可记录，与是否支付无关。 */
    private String channelTradeNo;
    private String transactionId;
    private OffsetDateTime paidAt;
    private Long paymentRecordId;
    /** 可支付截止时间；到点仍未支付则转 CLOSED。存量行为空，视为不过期。 */
    private OffsetDateTime expiresAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Version
    private Long version;
}
