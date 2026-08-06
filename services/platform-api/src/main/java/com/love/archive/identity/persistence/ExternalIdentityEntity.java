package com.love.archive.identity.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.love.archive.identity.domain.IdentityProvider;
import java.time.OffsetDateTime;

@TableName("external_identity")
public class ExternalIdentityEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userAccountId;
    private IdentityProvider provider;
    private byte[] subjectCiphertext;
    private String subjectHmac;
    private byte[] unionIdCiphertext;
    private String unionIdHmac;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserAccountId() { return userAccountId; }
    public void setUserAccountId(Long userAccountId) { this.userAccountId = userAccountId; }
    public IdentityProvider getProvider() { return provider; }
    public void setProvider(IdentityProvider provider) { this.provider = provider; }
    public byte[] getSubjectCiphertext() { return cloneOrNull(subjectCiphertext); }
    public void setSubjectCiphertext(byte[] subjectCiphertext) { this.subjectCiphertext = cloneOrNull(subjectCiphertext); }
    public String getSubjectHmac() { return subjectHmac; }
    public void setSubjectHmac(String subjectHmac) { this.subjectHmac = subjectHmac; }
    public byte[] getUnionIdCiphertext() { return cloneOrNull(unionIdCiphertext); }
    public void setUnionIdCiphertext(byte[] unionIdCiphertext) { this.unionIdCiphertext = cloneOrNull(unionIdCiphertext); }
    public String getUnionIdHmac() { return unionIdHmac; }
    public void setUnionIdHmac(String unionIdHmac) { this.unionIdHmac = unionIdHmac; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    private static byte[] cloneOrNull(byte[] value) {
        return value == null ? null : value.clone();
    }
}

