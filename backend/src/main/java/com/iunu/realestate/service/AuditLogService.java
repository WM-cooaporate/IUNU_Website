package com.iunu.realestate.service;

import com.iunu.realestate.dto.response.AuditLogResponse;
import com.iunu.realestate.entity.AuditAction;
import org.springframework.data.domain.Page;

import java.time.Duration;
import java.time.Instant;

/**
 * The admin audit trail. Call {@link #record} from <em>inside</em> the service
 * transaction that makes the change, so a change that rolls back leaves no row
 * behind and a row never describes something that did not happen.
 */
public interface AuditLogService {

    /** Records an action by the currently authenticated user. */
    void record(AuditAction action, String targetType, Object targetId, String summary);

    /** Records an action by an explicit actor - for login, where nobody is authenticated yet. */
    void recordFor(Long actorId, String actorEmail, AuditAction action,
                   String targetType, Object targetId, String summary);

    /** Whether {@code actorId} has signed in from {@code clientIp} within {@code window}. */
    boolean hasRecentLoginFrom(Long actorId, String clientIp, Duration window);

    Page<AuditLogResponse> list(AuditAction action, int page, int size);

    int purgeOlderThan(Instant cutoff);
}
