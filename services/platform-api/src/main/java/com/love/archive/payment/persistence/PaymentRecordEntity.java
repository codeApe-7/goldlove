package com.love.archive.payment.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.love.archive.payment.domain.PaymentStatus;
import java.time.OffsetDateTime;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserAccountId() { return userAccountId; }
    public void setUserAccountId(Long userAccountId) { this.userAccountId = userAccountId; }
    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
    public Long getAmountMinor() { return amountMinor; }
    public void setAmountMinor(Long amountMinor) { this.amountMinor = amountMinor; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public OffsetDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(OffsetDateTime paidAt) { this.paidAt = paidAt; }
    public Long getOperatorAdminId() { return operatorAdminId; }
    public void setOperatorAdminId(Long operatorAdminId) { this.operatorAdminId = operatorAdminId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

