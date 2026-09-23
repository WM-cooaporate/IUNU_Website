package com.iunu.realestate.service.impl;

import com.iunu.realestate.dto.response.AuditLogResponse;
import com.iunu.realestate.entity.AuditAction;
import com.iunu.realestate.entity.AuditLogEntry;
import com.iunu.realestate.security.RequestIdFilter;
import com.iunu.realestate.repository.AuditLogRepository;
import com.iunu.realestate.security.UserPrincipal;
import com.iunu.realestate.security.events.SecurityEvents;
import com.iunu.realestate.service.AuditLogService;
import com.iunu.realestate.util.LogSanitizer;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    /** The Activity tab's page-size ceiling, independent of the global pageable cap. */
    static final int MAX_PAGE_SIZE = 50;

    private final AuditLogRepository repository;
    private final SecurityEvents securityEvents;

    @Override
    @Transactional
    public void record(AuditAction action, String targetType, Object targetId, String summary) {
        Long actorId = null;
        String actorEmail = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            actorId = principal.getId();
            actorEmail = principal.getEmail();
        }
        recordFor(actorId, actorEmail, action, targetType, targetId, summary);
    }

    @Override
    @Transactional
    public void recordFor(Long actorId, String actorEmail, AuditAction action,
                          String targetType, Object targetId, String summary) {
        repository.save(AuditLogEntry.builder()
                .occurredAt(Instant.now())
                .actorId(actorId)
                .actorEmail(truncate(actorEmail, 255))
                .action(action)
                .targetType(truncate(targetType, 32))
                .targetId(targetId == null ? null : truncate(String.valueOf(targetId), 64))
                .clientIp(truncate(securityEvents.currentClientIp(), 64))
                .requestId(truncate(MDC.get(RequestIdFilter.MDC_KEY), 36))
                .summary(truncate(LogSanitizer.forLog(summary), 500))
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasRecentLoginFrom(Long actorId, String clientIp, Duration window) {
        return repository.existsByActorIdAndActionAndClientIpAndOccurredAtAfter(
                actorId, AuditAction.LOGIN_SUCCEEDED, truncate(clientIp, 64), Instant.now().minus(window));
    }

    /**
     * Newest first, always. The sort is fixed here rather than taken from the
     * request, so ?sort= cannot be used to walk the table by some other column.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> list(AuditAction action, int page, int size) {
        PageRequest request = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE));
        Page<AuditLogEntry> entries = (action == null)
                ? repository.findAllByOrderByOccurredAtDescIdDesc(request)
                : repository.findByActionOrderByOccurredAtDescIdDesc(action, request);
        return entries.map(AuditLogResponse::from);
    }

    @Override
    @Transactional
    public int purgeOlderThan(Instant cutoff) {
        return repository.deleteOlderThan(cutoff);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
