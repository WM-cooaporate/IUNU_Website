package com.iunu.realestate.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iunu.realestate.entity.Role;
import com.iunu.realestate.entity.User;
import com.iunu.realestate.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static com.iunu.realestate.auth.RefreshFlows.login;
import static com.iunu.realestate.auth.RefreshFlows.raceTwoRefreshes;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The N1 race again, on the database that runs in production. H2's locking is
 * not PostgreSQL's: what matters here is that under READ COMMITTED the losing
 * UPDATE waits on the row lock, then re-evaluates {@code revoked = false}
 * against the committed row and matches nothing. That is PostgreSQL
 * behaviour, and only PostgreSQL can prove it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@DisplayName("Refresh-token race on PostgreSQL")
class RefreshTokenRacePostgresTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("two concurrent refreshes with one token: exactly one wins, 20 times running")
    void concurrentRefreshHasExactlyOneWinner() throws Exception {
        for (int round = 0; round < 20; round++) {
            User user = userRepository.save(User.builder()
                    .fullName("Race " + round)
                    .email("race-" + System.nanoTime() + "@iunu.test")
                    .phone("+20 100 000 0000")
                    .password(passwordEncoder.encode("Password1"))
                    .role(Role.USER)
                    .build());
            String token = login(mockMvc, objectMapper, user.getEmail(), "Password1").get("refreshToken").asText();

            List<Integer> statuses = raceTwoRefreshes(mockMvc, objectMapper, token);
            assertThat(statuses).as("round %d", round).containsExactlyInAnyOrder(200, 401);
        }
    }
}
