package com.love.archive.identity.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.love.archive.identity.domain.ActivationCodeStatus;
import com.love.archive.identity.domain.MembershipTier;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("activation_code")
public class ActivationCodeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String boundPhone;
    private MembershipTier grantedTier;
    private ActivationCodeStatus status;
    private Long redeemedByAccountId;
    private OffsetDateTime redeemedAt;
    private Long createdByAdminId;
    private String note;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Version
    private Long version;
}
