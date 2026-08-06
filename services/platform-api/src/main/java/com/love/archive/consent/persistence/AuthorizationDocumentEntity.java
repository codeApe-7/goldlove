package com.love.archive.consent.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.love.archive.consent.domain.AuthorizationDocumentStatus;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("authorization_document")
public class AuthorizationDocumentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("document_code")
    private String documentCode;
    @TableField("version")
    private String version;
    @TableField("title")
    private String title;
    @TableField("content")
    private String content;
    @TableField("content_sha256")
    private String contentSha256;
    @TableField("status")
    private AuthorizationDocumentStatus status;
    @TableField("effective_at")
    private OffsetDateTime effectiveAt;
    @TableField("created_by_admin_id")
    private Long createdByAdminId;
    @TableField("created_at")
    private OffsetDateTime createdAt;
}
