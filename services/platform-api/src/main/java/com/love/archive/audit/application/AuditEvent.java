package com.love.archive.audit.application;

import java.time.OffsetDateTime;

public record AuditEvent(
        ActorType actorType,
        Long actorId,
        String action,
        String targetType,
        Long targetId,
        String requestId,
        String metadata,
        OffsetDateTime occurredAt) {

    public enum ActorType {
        ADMIN,
        GUEST,
        SYSTEM
    }
}
