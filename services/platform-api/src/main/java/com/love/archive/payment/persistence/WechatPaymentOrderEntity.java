package com.love.archive.payment.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.love.archive.payment.domain.PaymentChannelType;
import com.love.archive.payment.domain.WechatOrderStatus;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("wechat_payment_order")
public class WechatPaymentOrderEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String outTradeNo;
    private PaymentChannelType channel;
    private byte[] openidCiphertext;
    private String openidHmac;
    /** 规范化手机号的 HMAC；本模块不持有手机号明文或密文。 */
    private String phoneToken;
    private String description;
    private Long amountMinor;
    private String currency;
    private WechatOrderStatus status;
    private String prepayId;
    private byte[] transactionIdCiphertext;
    private String transactionIdHmac;
    private Long presentedAuthorizationDocumentId;
    private Long paymentRecordId;
    private Long paidAmountMinor;
    private OffsetDateTime paidAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Version
    private Long version;

    public byte[] getOpenidCiphertext() { return cloneOrNull(openidCiphertext); }
    public void setOpenidCiphertext(byte[] openidCiphertext) { this.openidCiphertext = cloneOrNull(openidCiphertext); }
    public byte[] getTransactionIdCiphertext() { return cloneOrNull(transactionIdCiphertext); }
    public void setTransactionIdCiphertext(byte[] transactionIdCiphertext) {
        this.transactionIdCiphertext = cloneOrNull(transactionIdCiphertext);
    }

    private static byte[] cloneOrNull(byte[] value) {
        return value == null ? null : value.clone();
    }
}
