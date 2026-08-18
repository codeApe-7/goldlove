package com.love.archive.payment.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.love.archive.payment.domain.RegistrationTokenStatus;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("registration_token")
public class RegistrationTokenEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tokenHmac;
    private Long wechatPaymentOrderId;
    private String outTradeNo;
    private byte[] openidCiphertext;
    private String openidHmac;
    private RegistrationTokenStatus status;
    private OffsetDateTime expiresAt;
    private OffsetDateTime usedAt;
    private Long userAccountId;
    private OffsetDateTime createdAt;

    public byte[] getOpenidCiphertext() { return openidCiphertext == null ? null : openidCiphertext.clone(); }
    public void setOpenidCiphertext(byte[] openidCiphertext) {
        this.openidCiphertext = openidCiphertext == null ? null : openidCiphertext.clone();
    }
}
