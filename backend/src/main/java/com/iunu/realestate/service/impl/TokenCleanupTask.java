package com.iunu.realestate.service.impl;

import com.iunu.realestate.repository.PasswordResetTokenRepository;
import com.iunu.realestate.repository.RefreshTokenRepository;
import com.iunu.realestate.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Periodically purges expired tokens and old audit rows so the tables don't
 * grow forever.
 *
 * <p>Refresh tokens are purged on <em>expiry</em> only. This used to delete
 * revoked rows too, which quietly disabled reuse detection overnight: a rotated
 * token deleted at 03:00 and replayed at 03:05 looked like an unknown token
 * rather than a stolen one.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupTask {

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final AuditLogService auditLogService;

    /** How long an audit row is kept. A year covers "what changed since last season" questions. */
    @Value("${app.audit.retention-days:365}")
    private long auditRetentionDays;

    @Scheduled(cron = "0 0 3 * * *") // daily at 03:00 server time
    @Transactional
    public void purgeExpiredTokens() {
        Instant now = Instant.now();
        refreshTokenRepository.deleteExpired(now);
        passwordResetTokenRepository.deleteExpired(now);
        int auditRows = auditLogService.purgeOlderThan(now.minus(auditRetentionDays, ChronoUnit.DAYS));
        log.debug("Expired token cleanup completed; {} audit rows past retention removed", auditRows);
    }
}
