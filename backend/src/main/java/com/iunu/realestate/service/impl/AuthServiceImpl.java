package com.iunu.realestate.service.impl;

import com.iunu.realestate.dto.request.*;
import com.iunu.realestate.dto.response.AuthResponse;
import com.iunu.realestate.dto.response.MessageResponse;
import com.iunu.realestate.dto.response.UserResponse;
import com.iunu.realestate.entity.AuditAction;
import com.iunu.realestate.entity.PasswordResetToken;
import com.iunu.realestate.entity.RefreshToken;
import com.iunu.realestate.entity.RevocationReason;
import com.iunu.realestate.entity.Role;
import com.iunu.realestate.entity.User;
import com.iunu.realestate.exception.BadRequestException;
import com.iunu.realestate.exception.UnauthorizedException;
import com.iunu.realestate.metrics.AbuseMetrics;
import com.iunu.realestate.repository.PasswordResetTokenRepository;
import com.iunu.realestate.repository.RefreshTokenRepository;
import com.iunu.realestate.repository.UserRepository;
import com.iunu.realestate.security.JwtService;
import com.iunu.realestate.security.events.SecurityEventType;
import com.iunu.realestate.security.events.SecurityEvents;
import com.iunu.realestate.security.lockout.LoginAttemptStore;
import com.iunu.realestate.service.AdminSignInAlerter;
import com.iunu.realestate.service.AuditLogService;
import com.iunu.realestate.service.AuthService;
import com.iunu.realestate.service.EmailService;
import com.iunu.realestate.util.LogSanitizer;
import com.iunu.realestate.util.TokenHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final AbuseMetrics metrics;
    private final SecurityEvents securityEvents;
    private final AuditLogService auditLogService;
    private final AdminSignInAlerter adminSignInAlerter;
    private final LoginAttemptStore loginAttempts;

    @Value("${app.security.reset-token-expiry-minutes:30}")
    private long resetTokenExpiryMinutes;

    @Value("${app.security.refresh-token-expiry-days:7}")
    private long refreshTokenExpiryDays;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * How long after a rotation the old refresh token may be presented again
     * without being treated as stolen. Two tabs refreshing at the same moment
     * is normal and must not sign the user out everywhere; a rotated token
     * turning up minutes later is not. The window never issues a token - it
     * only decides whether a late replay burns the whole family.
     */
    @Value("${app.security.refresh-reuse-grace-seconds:10}")
    private long refreshReuseGraceSeconds;

    /** An admin sign-in from an address not seen in this long raises ADMIN_LOGIN_NEW_IP. */
    private static final Duration KNOWN_ADMIN_IP_WINDOW = Duration.ofDays(90);

    /**
     * One message for every refresh failure, whichever branch produced it, so
     * a caller replaying tokens learns nothing about which check they tripped.
     */
    /** The one answer to every failed login: wrong password, unknown account, or any kind of lock. */
    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private static final String INVALID_REFRESH_TOKEN = "Invalid or expired refresh token";

    private static final String GENERIC_FORGOT_PASSWORD_MESSAGE =
            "If an account with that email exists, a password reset link has been sent.";

    @Override
    @Transactional
    public MessageResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BadRequestException("An account with this email already exists");
        }

        User user = User.builder()
                .fullName(request.fullName().trim())
                .email(normalizedEmail)
                .phone(request.phone().trim())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .build();

        userRepository.save(user);
        log.info("New user registered: {}", LogSanitizer.maskEmail(normalizedEmail));

        return new MessageResponse("Account created successfully.");
    }

    /**
     * Signs a user in, with brute-force protection that cannot be turned
     * against the account's owner from one address (N5, M9 - see
     * {@link LoginAttemptStore}).
     *
     * <p>Failures are counted in memory, never in the database. The old
     * counter was a column written inside this transaction and rolled back by
     * the UnauthorizedException reporting the failure, so accounts never
     * locked at all.
     *
     * <p>A locked pair or account gets exactly the same response as a wrong
     * password, and the password is still checked first, so neither the body
     * nor the timing says a lock exists.
     */
    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        String clientIp = securityEvents.currentClientIp();

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        String accountKey = user == null ? null : String.valueOf(user.getId());
        boolean locked = accountKey != null && loginAttempts.isLocked(accountKey, clientIp);

        boolean authenticated;
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.password()));
            authenticated = true;
        } catch (LockedException | DisabledException e) {
            // An account flagged by hand in the database (account_locked or
            // enabled columns). Same answer as everything else.
            securityEvents.record(SecurityEventType.LOGIN_FAILED, user == null ? null : user.getId(),
                    normalizedEmail, Map.of("reason", "account_disabled"));
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        } catch (BadCredentialsException e) {
            authenticated = false;
        }

        if (locked) {
            // Refused whether or not the password was right. Not counted again,
            // so a locked-out attacker cannot extend the lock.
            securityEvents.record(SecurityEventType.LOGIN_FAILED, user.getId(), normalizedEmail, clientIp,
                    Map.of("reason", "locked", "passwordCorrect", String.valueOf(authenticated)));
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }

        if (!authenticated) {
            // Counted whether or not the account exists, so the metric reflects
            // guessing attempts rather than only attempts against real users.
            metrics.loginFailed();
            securityEvents.record(SecurityEventType.LOGIN_FAILED, user == null ? null : user.getId(),
                    normalizedEmail, clientIp,
                    Map.of("reason", "bad_credentials", "knownAccount", String.valueOf(user != null)));
            if (user != null) {
                registerFailedAttempt(user, clientIp);
            }
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }

        // Authentication succeeded; `user` is guaranteed non-null here since
        // DaoAuthenticationProvider would otherwise have thrown BadCredentialsException.
        loginAttempts.recordSuccess(accountKey, clientIp);

        log.info("User logged in: {}", LogSanitizer.maskEmail(normalizedEmail));
        if (user.getRole() == Role.ADMIN) {
            recordAdminSignIn(user);
        }
        return issueTokenPair(user);
    }

    /**
     * Rotates a refresh token: the presented one is consumed and a new pair
     * issued. Two holes this closes, both about a copied token:
     *
     * <p><strong>Rotation is atomic.</strong> The token is consumed by one
     * conditional UPDATE ({@link RefreshTokenRepository#consume}); if two
     * requests race with the same token, exactly one wins and the other gets a
     * 401. Previously both could read "not revoked" and both get a new pair,
     * so a thief and the real user each kept a session alive indefinitely.
     *
     * <p><strong>Replay is detected.</strong> A token that was rotated and is
     * presented again after the grace window has been copied - the legitimate
     * client already has its successor. That revokes every live token of the
     * user (the thief's and the real user's alike, since they cannot be told
     * apart) and raises REFRESH_REUSE_DETECTED, the most specific sign of a
     * stolen token this API can see.
     *
     * <p>noRollbackFor, because the family revocation has to survive the 401
     * thrown straight after it. UnauthorizedException is a RuntimeException,
     * so a plain @Transactional would roll the revocation back and leave the
     * thief's token working.
     */
    @Override
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResponse refresh(RefreshTokenRequest request) {
        String hash = TokenHasher.sha256Hex(request.refreshToken());
        Instant now = Instant.now();

        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException(INVALID_REFRESH_TOKEN));
        // Read now, while the lazy user is still attached: both modifying
        // queries below clear the persistence context, and a detached proxy
        // cannot be initialised afterwards.
        Long userId = existing.getUser().getId();
        String email = existing.getUser().getEmail();
        User user = existing.getUser();

        if (existing.isRevoked()) {
            if (isReplayOfRotatedToken(existing, now)) {
                int revoked = refreshTokenRepository.revokeAllForUser(user, now, RevocationReason.REUSE_DETECTED);
                securityEvents.record(SecurityEventType.REFRESH_REUSE_DETECTED, userId, email,
                        Map.of("tokenId", String.valueOf(existing.getId()),
                                "familyRevoked", String.valueOf(revoked)));
            }
            // Inside the grace window, or revoked for any other reason: a plain
            // no, with no side effects.
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
        }

        if (!existing.getExpiresAt().isAfter(now)) {
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
        }

        if (refreshTokenRepository.consume(existing.getId(), now, RevocationReason.ROTATED) == 0) {
            // A concurrent request consumed it between our read and our
            // update. Usually two tabs; logged, never alerted on.
            securityEvents.record(SecurityEventType.REFRESH_RACE_LOST, userId, email,
                    Map.of("tokenId", String.valueOf(existing.getId())));
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
        }

        return issueTokenPair(userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException(INVALID_REFRESH_TOKEN)));
    }

    private boolean isReplayOfRotatedToken(RefreshToken token, Instant now) {
        return token.getRevokedReason() == RevocationReason.ROTATED
                && token.getRevokedAt() != null
                && token.getRevokedAt().isBefore(now.minusSeconds(refreshReuseGraceSeconds));
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest request) {
        String hash = TokenHasher.sha256Hex(request.refreshToken());
        // Only a live token changes. Logging out with an already-rotated token
        // must not overwrite ROTATED, or a later replay of it would no longer
        // be recognised as one.
        refreshTokenRepository.findByTokenHash(hash)
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> refreshTokenRepository.consume(token.getId(), Instant.now(), RevocationReason.LOGOUT));
    }

    @Override
    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        // Recorded before the lookup and with no account detail, so this line
        // adds nothing to the timing difference M8 describes.
        securityEvents.record(SecurityEventType.PASSWORD_RESET_REQUESTED, null, normalizedEmail, null);

        userRepository.findByEmailIgnoreCase(normalizedEmail).ifPresent(user -> {
            passwordResetTokenRepository.invalidateAllForUser(user);

            String rawToken = TokenHasher.generateRawToken();
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(TokenHasher.sha256Hex(rawToken))
                    .expiresAt(Instant.now().plus(resetTokenExpiryMinutes, ChronoUnit.MINUTES))
                    .build();
            passwordResetTokenRepository.save(resetToken);

            String resetLink = frontendUrl + "/reset-password?token=" + rawToken;
            emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), resetLink);
            log.info("Password reset requested for {}", LogSanitizer.maskEmail(normalizedEmail));
        });

        // Same response whether or not the account exists, by design.
        return new MessageResponse(GENERIC_FORGOT_PASSWORD_MESSAGE);
    }

    @Override
    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        String hash = TokenHasher.sha256Hex(request.token());

        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        if (resetToken.isUsed() || resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Invalid or expired reset token");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        // Whoever reset the password proved they own the mailbox; every lock
        // on the account, from any address, is lifted.
        loginAttempts.clearAccount(String.valueOf(user.getId()));

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
        passwordResetTokenRepository.invalidateAllForUser(user);

        // Force re-authentication everywhere after a password reset.
        refreshTokenRepository.revokeAllForUser(user, Instant.now(), RevocationReason.PASSWORD_RESET);

        securityEvents.record(SecurityEventType.PASSWORD_RESET_COMPLETED, user.getId(), user.getEmail(), null);
        return new MessageResponse("Your password has been reset successfully. Please log in again.");
    }

    @Override
    @Transactional
    public MessageResponse changePassword(String currentUserEmail, ChangePasswordRequest request) {
        User user = userRepository.findByEmailIgnoreCase(currentUserEmail)
                .orElseThrow(() -> new UnauthorizedException("Invalid session"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        refreshTokenRepository.revokeAllForUser(user, Instant.now(), RevocationReason.PASSWORD_CHANGED);

        securityEvents.record(SecurityEventType.PASSWORD_CHANGED, user.getId(), user.getEmail(), null);
        if (user.getRole() == Role.ADMIN) {
            auditLogService.recordFor(user.getId(), user.getEmail(), AuditAction.PASSWORD_CHANGED,
                    "USER", user.getId(), "password changed; all sessions signed out");
        }

        return new MessageResponse("Password changed successfully. Please log in again.");
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid session"));
        return UserResponse.from(user);
    }

    private void registerFailedAttempt(User user, String clientIp) {
        LoginAttemptStore.FailureOutcome outcome = loginAttempts.recordFailure(String.valueOf(user.getId()), clientIp);
        if (outcome.pairLockedNow()) {
            // One source locked itself out of one account. Logged, not alerted:
            // it inconveniences nobody but the sender.
            log.info("Login locked for {} from one address after {} failures",
                    LogSanitizer.maskEmail(user.getEmail()), outcome.pairFailures());
        }
        if (outcome.accountLockedNow()) {
            // The distributed case: the real owner is locked out too, so a
            // person has to hear about it.
            metrics.accountLocked();
            securityEvents.record(SecurityEventType.ACCOUNT_LOCKED, user.getId(), user.getEmail(), clientIp,
                    Map.of("scope", "account", "failuresLastHour", String.valueOf(outcome.accountFailuresLastHour())));
        }
    }

    /**
     * Audit row, security event, and - for an address this admin has not used
     * in 90 days - ADMIN_LOGIN_NEW_IP plus an email to the admin. The check
     * runs before this login's own row is written, or it would always find it.
     *
     * <p>The email is sent asynchronously so an admin login takes the same
     * time whether or not an alert goes out.
     */
    private void recordAdminSignIn(User user) {
        String clientIp = securityEvents.currentClientIp();
        securityEvents.record(SecurityEventType.LOGIN_SUCCEEDED_ADMIN, user.getId(), user.getEmail(), clientIp, null);

        if (!auditLogService.hasRecentLoginFrom(user.getId(), clientIp, KNOWN_ADMIN_IP_WINDOW)) {
            securityEvents.record(SecurityEventType.ADMIN_LOGIN_NEW_IP, user.getId(), user.getEmail(), clientIp,
                    Map.of("windowDays", String.valueOf(KNOWN_ADMIN_IP_WINDOW.toDays())));
            adminSignInAlerter.alertNewSignIn(user.getEmail(), user.getFullName(), clientIp, Instant.now());
        }

        auditLogService.recordFor(user.getId(), user.getEmail(), AuditAction.LOGIN_SUCCEEDED,
                "USER", user.getId(), "admin signed in");
    }

    private AuthResponse issueTokenPair(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());

        String rawRefreshToken = TokenHasher.generateRawToken();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(TokenHasher.sha256Hex(rawRefreshToken))
                .expiresAt(Instant.now().plus(refreshTokenExpiryDays, ChronoUnit.DAYS))
                .build();
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.of(
                accessToken,
                rawRefreshToken,
                jwtService.getAccessTokenExpirationSeconds(),
                UserResponse.from(user)
        );
    }
}
