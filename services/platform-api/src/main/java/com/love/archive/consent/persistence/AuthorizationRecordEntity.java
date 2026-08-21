package com.love.archive.consent.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("authorization_record")
public class AuthorizationRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("user_account_id")
    private Long userAccountId;
    @TableField("authorization_document_id")
    private Long authorizationDocumentId;
    @TableField("accepted")
    private Boolean accepted;
    @TableField("accepted_at")
    private OffsetDateTime acceptedAt;
    @TableField("effective_at")
    private OffsetDateTime effectiveAt;
    @TableField("expires_at")
    private OffsetDateTime expiresAt;
    @TableField("source_page")
    private String sourcePage;
    @TableField("client_ip")
    private String clientIp;
    @TableField("user_agent")
    private String userAgent;
    @TableField("session_reference")
    private String sessionReference;
    @TableField("created_at")
    private OffsetDateTime createdAt;
}
