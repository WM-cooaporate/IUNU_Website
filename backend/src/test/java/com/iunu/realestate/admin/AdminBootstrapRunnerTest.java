package com.iunu.realestate.admin;

import com.iunu.realestate.config.AdminBootstrapRunner;
import com.iunu.realestate.entity.Role;
import com.iunu.realestate.repository.UserRepository;
import com.iunu.realestate.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bootstrap runner is the only path to a first admin, so its guards are
 * worth pinning down: no env vars means no account, a short password means no
 * account, and an existing ADMIN means it stays a no-op however often it runs.
 */
@DisplayName("AdminBootstrapRunner")
class AdminBootstrapRunnerTest extends IntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;

    private AdminBootstrapRunner runnerWith(String email, String password) {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(users, encoder);
        ReflectionTestUtils.setField(runner, "adminEmail", email);
        ReflectionTestUtils.setField(runner, "adminPassword", password);
        return runner;
    }

    @Test
    @DisplayName("does nothing when the env vars are absent")
    void noopWithoutEnvVars() {
        users.deleteAll();
        runnerWith("", "").run(null);
        assertThat(users.existsByRole(Role.ADMIN)).isFalse();
    }

    @Test
    @DisplayName("refuses to create the account when ADMIN_PASSWORD is under 8 characters")
    void refusesShortPassword() {
        users.deleteAll();
        runnerWith("first@iunu.test", "short1").run(null);
        assertThat(users.existsByRole(Role.ADMIN)).isFalse();
        assertThat(users.findByEmailIgnoreCase("first@iunu.test")).isEmpty();
    }

    @Test
    @DisplayName("creates exactly one ADMIN, with the password hashed, and is idempotent on re-run")
    void createsFirstAdminOnceOnly() {
        users.deleteAll();
        AdminBootstrapRunner runner = runnerWith("First.Admin@IUNU.test", "Password1");

        runner.run(null);
        runner.run(null);
        runner.run(null);

        assertThat(users.count()).isEqualTo(1);
        var admin = users.findByEmailIgnoreCase("first.admin@iunu.test").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getPassword()).isNotEqualTo("Password1");
        assertThat(encoder.matches("Password1", admin.getPassword())).isTrue();
    }

    @Test
    @DisplayName("will not add a second admin once one exists, even under a different email")
    void doesNotAddSecondAdmin() {
        users.deleteAll();
        createUser("existing@iunu.test", "Password1", Role.ADMIN);

        runnerWith("another@iunu.test", "Password1").run(null);

        assertThat(users.findByEmailIgnoreCase("another@iunu.test")).isEmpty();
        assertThat(users.count()).isEqualTo(1);
    }
}
