package com.love.archive.identity.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
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

}
