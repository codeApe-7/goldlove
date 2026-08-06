package com.love.archive.audit.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.love.archive.audit.domain.AuditActorType;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("audit_log")
public class AuditLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private AuditActorType actorType;
    private Long actorId;
    private String action;
    private String targetType;
    private Long targetId;
    private String requestId;
    private String metadata;
    private OffsetDateTime occurredAt;

}
