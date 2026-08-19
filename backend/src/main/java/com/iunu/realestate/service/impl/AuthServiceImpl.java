package com.iunu.realestate.service.impl;

import com.iunu.realestate.dto.request.*;
import com.iunu.realestate.dto.response.AuthResponse;
import com.iunu.realestate.dto.response.MessageResponse;
import com.iunu.realestate.dto.response.UserResponse;
import com.iunu.realestate.entity.PasswordResetToken;
import com.iunu.realestate.entity.RefreshToken;
import com.iunu.realestate.entity.Role;
import com.iunu.realestate.entity.User;
import com.iunu.realestate.exception.AccountLockedException;
import com.iunu.realestate.exception.BadRequestException;
import com.iunu.realestate.exception.UnauthorizedException;
import com.iunu.realestate.repository.PasswordResetTokenRepository;
import com.iunu.realestate.repository.RefreshTokenRepository;
import com.iunu.realestate.repository.UserRepository;
import com.iunu.realestate.security.JwtService;
import com.iunu.realestate.service.AuthService;
import com.iunu.realestate.service.EmailService;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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

    @Value("${app.security.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${app.security.lock-duration-minutes:15}")
    private long lockDurationMinutes;

    @Value("${app.security.reset-token-expiry-minutes:30}")
    private long resetTokenExpiryMinutes;

    @Value("${app.security.refresh-token-expiry-days:7}")
    private long refreshTokenExpiryDays;

    @Value("${app.frontend-url}")
    private String frontendUrl;

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
        log.info("New user registered: {}", normalizedEmail);

        return new MessageResponse("Account created successfully.");
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        unlockIfLockExpired(user);

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.password()));
        } catch (LockedException e) {
            throw new AccountLockedException(
                    "This account is temporarily locked due to multiple failed login attempts. Please try again later.");
        } catch (DisabledException e) {
            throw new UnauthorizedException("Invalid email or password");
        } catch (BadCredentialsException e) {
            if (user != null) {
                registerFailedAttempt(user);
            }
            throw new UnauthorizedException("Invalid email or password");
        }

        // Authentication succeeded; `user` is guaranteed non-null here since
        // DaoAuthenticationProvider would otherwise have thrown BadCredentialsException.
        if (user.getFailedLoginAttempts() > 0 || user.isAccountLocked()) {
            user.setFailedLoginAttempts(0);
            user.setAccountLocked(false);
            user.setLockedUntil(null);
            userRepository.save(user);
        }

        log.info("User logged in: {}", normalizedEmail);
        return issueTokenPair(user);
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String hash = TokenHasher.sha256Hex(request.refreshToken());

        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token"));

        if (existing.isRevoked() || existing.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        return issueTokenPair(existing.getUser());
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest request) {
        String hash = TokenHasher.sha256Hex(request.refreshToken());
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    @Override
    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

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
            log.info("Password reset requested for {}", normalizedEmail);
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
        user.setFailedLoginAttempts(0);
        user.setAccountLocked(false);
        user.setLockedUntil(null);
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
        passwordResetTokenRepository.invalidateAllForUser(user);

        // Force re-authentication everywhere after a password reset.
        refreshTokenRepository.revokeAllForUser(user);

        log.info("Password reset completed for {}", user.getEmail());
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

        refreshTokenRepository.revokeAllForUser(user);

        return new MessageResponse("Password changed successfully. Please log in again.");
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid session"));
        return UserResponse.from(user);
    }

    private void registerFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= maxFailedAttempts) {
            user.setAccountLocked(true);
            user.setLockedUntil(Instant.now().plus(lockDurationMinutes, ChronoUnit.MINUTES));
            log.warn("Account locked after {} failed attempts: {}", attempts, user.getEmail());
        }

        userRepository.save(user);
    }

    private void unlockIfLockExpired(User user) {
        if (user != null && user.isAccountLocked() && user.getLockedUntil() != null
                && user.getLockedUntil().isBefore(Instant.now())) {
            user.setAccountLocked(false);
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
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
