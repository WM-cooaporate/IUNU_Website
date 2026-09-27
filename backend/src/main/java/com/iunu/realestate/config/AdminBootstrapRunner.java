package com.iunu.realestate.config;

import com.iunu.realestate.entity.Role;
import com.iunu.realestate.entity.User;
import com.iunu.realestate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first ADMIN user on startup, from environment variables only
 * and only while the database has no ADMIN at all. No default admin
 * credentials are ever baked into the codebase or a migration - that would
 * be a standing, well-known-by-attackers backdoor.
 *
 * The gate is "does any ADMIN exist", not "does this email exist", so the
 * env vars cannot be used to quietly add a second admin to a running
 * deployment; that requires an authenticated POST /api/admin/users.
 *
 * Idempotent: safe to restart with the vars still set. Once you have an
 * admin, unset ADMIN_EMAIL/ADMIN_PASSWORD anyway - see the README.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_EMAIL:}")
    private String adminEmail;

    @Value("${ADMIN_PASSWORD:}")
    private String adminPassword;

    private static final int MIN_PASSWORD_LENGTH = 8;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            return;
        }

        if (userRepository.existsByRole(Role.ADMIN)) {
            log.info("An ADMIN user already exists - skipping admin bootstrap. "
                    + "ADMIN_EMAIL/ADMIN_PASSWORD can be unset; create further admins "
                    + "via POST /api/admin/users.");
            return;
        }

        if (adminPassword.length() < MIN_PASSWORD_LENGTH) {
            log.error("ADMIN_PASSWORD is {} characters; at least {} are required. "
                            + "Refusing to create the admin account - no ADMIN user exists yet. "
                            + "Set a longer ADMIN_PASSWORD and restart.",
                    adminPassword.length(), MIN_PASSWORD_LENGTH);
            return;
        }

        String normalizedEmail = adminEmail.trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            log.error("Cannot bootstrap ADMIN: a non-admin account already uses {}. "
                    + "Choose a different ADMIN_EMAIL.", normalizedEmail);
            return;
        }

        User admin = User.builder()
                .fullName("Administrator")
                .email(normalizedEmail)
                .phone("N/A")
                .password(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .build();

        userRepository.save(admin);
        log.info("Bootstrapped initial ADMIN user: {}. Unset ADMIN_EMAIL and ADMIN_PASSWORD now.",
                normalizedEmail);
    }
}
