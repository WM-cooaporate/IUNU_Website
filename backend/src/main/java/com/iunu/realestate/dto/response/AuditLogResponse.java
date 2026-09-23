package com.iunu.realestate.dto.response;

import com.iunu.realestate.entity.AuditAction;
import com.iunu.realestate.entity.AuditLogEntry;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        Instant occurredAt,
        Long actorId,
        String actorEmail,
        AuditAction action,
        String targetType,
        String targetId,
        String clientIp,
        String requestId,
        String summary
) {
    public static AuditLogResponse from(AuditLogEntry entry) {
        return new AuditLogResponse(
                entry.getId(), entry.getOccurredAt(), entry.getActorId(), entry.getActorEmail(),
                entry.getAction(), entry.getTargetType(), entry.getTargetId(), entry.getClientIp(),
                entry.getRequestId(), entry.getSummary());
    }
}
