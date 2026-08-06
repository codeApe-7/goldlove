package com.love.archive.identity.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.love.archive.identity.domain.AccountStatus;
import java.time.OffsetDateTime;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public byte[] getPhoneCiphertext() { return phoneCiphertext == null ? null : phoneCiphertext.clone(); }
    public void setPhoneCiphertext(byte[] phoneCiphertext) { this.phoneCiphertext = phoneCiphertext == null ? null : phoneCiphertext.clone(); }
    public String getPhoneHmac() { return phoneHmac; }
    public void setPhoneHmac(String phoneHmac) { this.phoneHmac = phoneHmac; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public Long getCreatedByAdminId() { return createdByAdminId; }
    public void setCreatedByAdminId(Long createdByAdminId) { this.createdByAdminId = createdByAdminId; }
    public OffsetDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(OffsetDateTime activatedAt) { this.activatedAt = activatedAt; }
    public OffsetDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(OffsetDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

