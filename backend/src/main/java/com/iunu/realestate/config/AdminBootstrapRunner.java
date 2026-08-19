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
 * Creates the first ADMIN user on startup, but only from environment
 * variables and only if no user with that email exists yet. No default
 * admin credentials are ever baked into the codebase or a migration -
 * that would be a standing, well-known-by-attackers backdoor. Once you
 * have an admin, unset ADMIN_EMAIL/ADMIN_PASSWORD; this runner is a no-op
 * if either is missing.
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

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            return;
        }

        String normalizedEmail = adminEmail.trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            log.info("ADMIN_EMAIL {} already exists - skipping admin bootstrap", normalizedEmail);
            return;
        }

        if (adminPassword.length() < 8) {
            log.warn("ADMIN_PASSWORD is shorter than 8 characters - refusing to bootstrap admin user");
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
        log.info("Bootstrapped initial ADMIN user: {}", normalizedEmail);
    }
}
