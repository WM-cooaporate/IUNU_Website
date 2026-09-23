package com.iunu.realestate.audit;

import com.iunu.realestate.entity.AuditAction;
import com.iunu.realestate.entity.Role;
import com.iunu.realestate.entity.User;
import com.iunu.realestate.repository.AuditLogRepository;
import com.iunu.realestate.security.events.SecurityEvents;
import com.iunu.realestate.service.impl.EmailServiceImpl;
import com.iunu.realestate.support.IntegrationTest;
import com.iunu.realestate.support.LogCapture;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Admin sign-in from a new address")
class AdminNewIpLoginTest extends IntegrationTest {

    @Autowired private MeterRegistry meterRegistry;
    @Autowired private AuditLogRepository auditLogRepository;

    private double newIpEvents() {
        return meterRegistry.get(SecurityEvents.METRIC).tag("type", "ADMIN_LOGIN_NEW_IP").counter().count();
    }

    private void loginFrom(User user, String ip) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(ip);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", user.getEmail(), "password", "Password1"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the first login from an address raises the event; a second from the same address does not")
    void firstLoginFromAnAddressIsFlagged() throws Exception {
        User admin = createUser("newip-" + System.nanoTime() + "@iunu.test", "Password1", Role.ADMIN);

        try (LogCapture mail = new LogCapture(EmailServiceImpl.class.getName())) {
            double start = newIpEvents();
            loginFrom(admin, "198.51.100.7");
            assertThat(newIpEvents()).isEqualTo(start + 1);

            loginFrom(admin, "198.51.100.7");
            assertThat(newIpEvents()).isEqualTo(start + 1);

            loginFrom(admin, "203.0.113.9");
            assertThat(newIpEvents()).isEqualTo(start + 2);

            // The alert email goes out asynchronously - once per new address,
            // never with the raw address of the admin in the log line.
            Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
            while (mail.messages().stream().filter(m -> m.contains("new sign-in alert")).count() < 2
                    && Instant.now().isBefore(deadline)) {
                Thread.sleep(50);
            }
            assertThat(mail.messages()).filteredOn(m -> m.contains("new sign-in alert")).hasSize(2)
                    .allSatisfy(m -> assertThat(m).doesNotContain(admin.getEmail()));
        }

        assertThat(auditLogRepository.findAll()).filteredOn(row -> admin.getId().equals(row.getActorId())
                        && row.getAction() == AuditAction.LOGIN_SUCCEEDED)
                .hasSize(3)
                .extracting("clientIp").containsExactlyInAnyOrder("198.51.100.7", "198.51.100.7", "203.0.113.9");
    }

    @Test
    @DisplayName("a USER login writes no audit row and raises no admin event")
    void userLoginIsNotAudited() throws Exception {
        User user = createUser("plain-" + System.nanoTime() + "@iunu.test", "Password1", Role.USER);
        double start = newIpEvents();

        loginFrom(user, "198.51.100.99");

        assertThat(newIpEvents()).isEqualTo(start);
        assertThat(auditLogRepository.findAll()).noneMatch(row -> user.getId().equals(row.getActorId()));
    }
}
