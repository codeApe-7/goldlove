package com.love.archive.audit.application;

public interface AuditTrail {

    void append(AuditEvent event);
}
