package com.iunu.realestate.repository;

import com.iunu.realestate.entity.AuditAction;
import com.iunu.realestate.entity.AuditLogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

    Page<AuditLogEntry> findAllByOrderByOccurredAtDescIdDesc(Pageable pageable);

    Page<AuditLogEntry> findByActionOrderByOccurredAtDescIdDesc(AuditAction action, Pageable pageable);

    /** Served by idx_audit_log_actor_action. */
    boolean existsByActorIdAndActionAndClientIpAndOccurredAtAfter(
            Long actorId, AuditAction action, String clientIp, Instant after);

    @Modifying
    @Query("delete from AuditLogEntry a where a.occurredAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
