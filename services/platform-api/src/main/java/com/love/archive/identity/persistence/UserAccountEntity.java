package com.love.archive.identity.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.love.archive.identity.domain.AccountStatus;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("user_account")
public class UserAccountEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private byte[] phoneCiphertext;
    private String phoneHmac;
    private String passwordHash;
    private AccountStatus status;
    private Long createdByAdminId;
    private OffsetDateTime activatedAt;
    private OffsetDateTime lastLoginAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Version
    private Long version;

    public byte[] getPhoneCiphertext() { return phoneCiphertext == null ? null : phoneCiphertext.clone(); }
    public void setPhoneCiphertext(byte[] phoneCiphertext) { this.phoneCiphertext = phoneCiphertext == null ? null : phoneCiphertext.clone(); }
}
