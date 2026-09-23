package com.iunu.realestate.security;

import com.iunu.realestate.entity.PasswordResetToken;
import com.iunu.realestate.entity.Role;
import com.iunu.realestate.entity.User;
import com.iunu.realestate.repository.PasswordResetTokenRepository;
import com.iunu.realestate.security.events.SecurityEvents;
import com.iunu.realestate.security.lockout.LoginAttemptStore;
import com.iunu.realestate.support.IntegrationTest;
import com.iunu.realestate.util.TokenHasher;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * N5 (lockout never persisted) and M9 (lockout as a denial of service against
 * the admin), through real logins over HTTP.
 */
@DisplayName("Login lockout")
class LoginLockoutTest extends IntegrationTest {

    private static final String PASSWORD = "Password1";
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired private LoginAttemptStore loginAttempts;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private MeterRegistry meterRegistry;

    /** A client address no other test in the shared context has used. */
    private static String freshIp() {
        int n = NEXT_IP.getAndIncrement();
        return "10.66." + (n / 250) + "." + (n % 250 + 1);
    }

    private User newUser() {
        return createUser("lockout-" + System.nanoTime() + "@iunu.test", PASSWORD, Role.ADMIN);
    }

    private MvcResult login(User user, String password, String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(ip);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", user.getEmail(), "password", password))))
                .andReturn();
    }

    private double accountLockedEvents() {
        return meterRegistry.get(SecurityEvents.METRIC).tag("type", "ACCOUNT_LOCKED").counter().count();
    }

    @Test
    @DisplayName("a failed attempt is still counted after the exception that reports it (the N5 rollback trap)")
    void counterSurvivesTheException() throws Exception {
        User user = newUser();
        String ip = freshIp();

        MvcResult result = login(user, "wrong-password", ip);

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(loginAttempts.pairFailures(String.valueOf(user.getId()), ip)).isEqualTo(1);
    }

    @Test
    @DisplayName("five failures lock the attacker's address out, even with the right password; another address still signs in")
    void pairLockBlocksOnlyTheAttacker() throws Exception {
        User admin = newUser();
        String attacker = freshIp();
        String owner = freshIp();
        String wrongPasswordBody = login(admin, "wrong-0", attacker).getResponse().getContentAsString();
        for (int i = 1; i < 5; i++) {
            login(admin, "wrong-" + i, attacker);
        }

        MvcResult fromAttacker = login(admin, PASSWORD, attacker);
        assertThat(fromAttacker.getResponse().getStatus()).isEqualTo(401);
        // Same body as a wrong password: nothing says a lock exists.
        assertThat(message(fromAttacker.getResponse().getContentAsString())).isEqualTo(message(wrongPasswordBody));

        assertThat(login(admin, PASSWORD, owner).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("the 51st failure across addresses locks the account for everyone and raises ACCOUNT_LOCKED; a reset clears it")
    void distributedAttackLocksAccountUntilReset() throws Exception {
        User admin = newUser();
        double before = accountLockedEvents();

        for (int i = 0; i < 50; i++) {
            login(admin, "wrong-" + i, freshIp());
        }
        assertThat(accountLockedEvents()).as("50 failures is not yet a lock").isEqualTo(before);
        login(admin, "wrong-50", freshIp());
        assertThat(accountLockedEvents()).isEqualTo(before + 1);

        assertThat(login(admin, PASSWORD, freshIp()).getResponse().getStatus())
                .as("the owner is locked out too - the residual M9 risk").isEqualTo(401);

        String rawReset = TokenHasher.generateRawToken();
        passwordResetTokenRepository.save(PasswordResetToken.builder().user(admin)
                .tokenHash(TokenHasher.sha256Hex(rawReset))
                .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES)).build());
        mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", rawReset, "newPassword", "NewPassword2"))));

        assertThat(login(admin, "NewPassword2", freshIp()).getResponse().getStatus())
                .as("a completed reset lifts the lock").isEqualTo(200);
    }

    @Test
    @DisplayName("a completed reset also clears a pair lock")
    void resetClearsPairLock() throws Exception {
        User admin = newUser();
        String ip = freshIp();
        for (int i = 0; i < 5; i++) {
            login(admin, "wrong-" + i, ip);
        }
        String rawReset = TokenHasher.generateRawToken();
        passwordResetTokenRepository.save(PasswordResetToken.builder().user(admin)
                .tokenHash(TokenHasher.sha256Hex(rawReset))
                .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES)).build());
        mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", rawReset, "newPassword", "NewPassword2"))));

        assertThat(login(admin, "NewPassword2", ip).getResponse().getStatus()).isEqualTo(200);
    }

    private String message(String body) throws Exception {
        return objectMapper.readTree(body).get("message").asText();
    }
}
