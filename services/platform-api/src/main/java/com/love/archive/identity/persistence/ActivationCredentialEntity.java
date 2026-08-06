package com.love.archive.identity.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("activation_credential")
public class ActivationCredentialEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userAccountId;
    private String credentialHash;
    private OffsetDateTime expiresAt;
    private OffsetDateTime consumedAt;
    private Long createdByAdminId;
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserAccountId() { return userAccountId; }
    public void setUserAccountId(Long userAccountId) { this.userAccountId = userAccountId; }
    public String getCredentialHash() { return credentialHash; }
    public void setCredentialHash(String credentialHash) { this.credentialHash = credentialHash; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public OffsetDateTime getConsumedAt() { return consumedAt; }
    public void setConsumedAt(OffsetDateTime consumedAt) { this.consumedAt = consumedAt; }
    public Long getCreatedByAdminId() { return createdByAdminId; }
    public void setCreatedByAdminId(Long createdByAdminId) { this.createdByAdminId = createdByAdminId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

