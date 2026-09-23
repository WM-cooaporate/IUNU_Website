package com.iunu.realestate.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One row of the admin audit trail. Append-only: there are no setters, and
 * nothing in the application updates a row once written. See V7 for why the
 * table has no foreign key to {@code users}.
 */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_log_occurred_at", columnList = "occurredAt"),
        @Index(name = "idx_audit_log_actor_action", columnList = "actorId, action, occurredAt")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant occurredAt;

    private Long actorId;

    @Column(length = 255)
    private String actorEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private AuditAction action;

    @Column(length = 32)
    private String targetType;

    @Column(length = 64)
    private String targetId;

    @Column(length = 64)
    private String clientIp;

    @Column(length = 36)
    private String requestId;

    /** A fixed, short description of what changed - field names, never field values. */
    @Column(length = 500)
    private String summary;
}
