package com.love.archive.audit.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.love.archive.audit.domain.AuditActorType;
import java.time.OffsetDateTime;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AuditActorType getActorType() { return actorType; }
    public void setActorType(AuditActorType actorType) { this.actorType = actorType; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
}

