package com.love.archive.payment.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.love.archive.payment.domain.PaymentStatus;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("payment_record")
public class PaymentRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userAccountId;
    private String paymentReference;
    private Long amountMinor;
    private String currency;
    private PaymentStatus status;
    private OffsetDateTime paidAt;
    private Long operatorAdminId;
    private String note;
    private OffsetDateTime createdAt;

}
