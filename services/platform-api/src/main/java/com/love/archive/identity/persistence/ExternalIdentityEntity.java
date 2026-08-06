package com.love.archive.identity.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.love.archive.identity.domain.IdentityProvider;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
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

    public byte[] getSubjectCiphertext() { return cloneOrNull(subjectCiphertext); }
    public void setSubjectCiphertext(byte[] subjectCiphertext) { this.subjectCiphertext = cloneOrNull(subjectCiphertext); }
    public byte[] getUnionIdCiphertext() { return cloneOrNull(unionIdCiphertext); }
    public void setUnionIdCiphertext(byte[] unionIdCiphertext) { this.unionIdCiphertext = cloneOrNull(unionIdCiphertext); }

    private static byte[] cloneOrNull(byte[] value) {
        return value == null ? null : value.clone();
    }
}
