package com.love.archive.payment.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.love.archive.payment.domain.PaymentChannelType;
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
    private PaymentChannelType paymentChannel;
    private String outTradeNo;
    private byte[] transactionIdCiphertext;
    private String transactionIdHmac;
    private Long paidAmountMinor;
    private Long membershipCreditMinor;
    private Boolean registered;
    private OffsetDateTime paidAt;
    private Long operatorAdminId;
    @TableField("presented_authorization_document_id")
    private Long presentedAuthorizationDocumentId;
    private String note;
    private OffsetDateTime createdAt;

    public byte[] getTransactionIdCiphertext() {
        return transactionIdCiphertext == null ? null : transactionIdCiphertext.clone();
    }

    public void setTransactionIdCiphertext(byte[] transactionIdCiphertext) {
        this.transactionIdCiphertext = transactionIdCiphertext == null ? null : transactionIdCiphertext.clone();
    }
}
