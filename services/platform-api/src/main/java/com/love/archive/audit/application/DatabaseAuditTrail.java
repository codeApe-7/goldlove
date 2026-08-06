package com.love.archive.audit.application;

import com.love.archive.audit.domain.AuditActorType;
import com.love.archive.audit.persistence.AuditLogEntity;
import com.love.archive.audit.persistence.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class DatabaseAuditTrail implements AuditTrail {

    private final AuditLogMapper auditLogMapper;

    @Override
    public void append(AuditEvent event) {
        AuditLogEntity audit = new AuditLogEntity();
        audit.setActorType(AuditActorType.valueOf(event.actorType().name()));
        audit.setActorId(event.actorId());
        audit.setAction(event.action());
        audit.setTargetType(event.targetType());
        audit.setTargetId(event.targetId());
        audit.setRequestId(event.requestId());
        audit.setMetadata(event.metadata());
        audit.setOccurredAt(event.occurredAt());
        auditLogMapper.insert(audit);
    }
}
