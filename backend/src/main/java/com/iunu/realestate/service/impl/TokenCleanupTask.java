package com.iunu.realestate.service.impl;

import com.iunu.realestate.repository.PasswordResetTokenRepository;
import com.iunu.realestate.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Periodically purges expired/used tokens so the tables don't grow forever. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupTask {

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Scheduled(cron = "0 0 3 * * *") // daily at 03:00 server time
    @Transactional
    public void purgeExpiredTokens() {
        Instant now = Instant.now();
        refreshTokenRepository.deleteExpiredOrRevoked(now);
        passwordResetTokenRepository.deleteExpired(now);
        log.debug("Expired token cleanup completed");
    }
}
