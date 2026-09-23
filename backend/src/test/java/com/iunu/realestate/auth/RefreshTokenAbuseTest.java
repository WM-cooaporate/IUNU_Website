package com.iunu.realestate.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.iunu.realestate.entity.RefreshToken;
import com.iunu.realestate.entity.RevocationReason;
import com.iunu.realestate.entity.Role;
import com.iunu.realestate.entity.User;
import com.iunu.realestate.repository.PasswordResetTokenRepository;
import com.iunu.realestate.repository.RefreshTokenRepository;
import com.iunu.realestate.security.events.SecurityEvents;
import com.iunu.realestate.support.IntegrationTest;
import com.iunu.realestate.util.TokenHasher;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static com.iunu.realestate.auth.RefreshFlows.login;
import static com.iunu.realestate.auth.RefreshFlows.raceTwoRefreshes;
import static com.iunu.realestate.auth.RefreshFlows.refresh;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * N1 and N2 from the security audit addendum: refresh-token rotation that a
 * race could fork, and a replayed token that raised nothing.
 */
@DisplayName("Refresh-token abuse")
class RefreshTokenAbuseTest extends IntegrationTest {

    private static final String PASSWORD = "Password1";
    private static final String GENERIC_401 = "Invalid or expired refresh token";

    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MeterRegistry meterRegistry;

    private User newUser() {
        return createUser("refresh-" + System.nanoTime() + "@iunu.test", PASSWORD, Role.ADMIN);
    }

    private String loginRefreshToken(User user) throws Exception {
        return login(mockMvc, objectMapper, user.getEmail(), PASSWORD).get("refreshToken").asText();
    }

    private String rotate(String refreshToken) throws Exception {
        MvcResult result = refresh(mockMvc, objectMapper, refreshToken);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("refreshToken").asText();
    }

    /** Moves a token's revocation into the past, standing in for waiting out the grace window. */
    private void backdateRevocation(String rawToken, long seconds) {
        jdbcTemplate.update("UPDATE refresh_tokens SET revoked_at = ? WHERE token_hash = ?",
                java.sql.Timestamp.from(Instant.now().minus(seconds, ChronoUnit.SECONDS)),
                TokenHasher.sha256Hex(rawToken));
    }

    private RefreshToken row(String rawToken) {
        return refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawToken)).orElseThrow();
    }

    private double events(String type) {
        var counter = meterRegistry.find(SecurityEvents.METRIC).tag("type", type).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    @DisplayName("replaying a rotated token after the grace window kills the whole family")
    void replayAfterGraceRevokesFamily() throws Exception {
        User user = newUser();
        String stolen = loginRefreshToken(user);
        String legitimate = rotate(stolen);
        backdateRevocation(stolen, 60);
        double before = events("REFRESH_REUSE_DETECTED");

        MvcResult replay = refresh(mockMvc, objectMapper, stolen);
        assertThat(replay.getResponse().getStatus()).isEqualTo(401);
        assertThat(replay.getResponse().getContentAsString()).contains(GENERIC_401);

        // The token the real client got from the legitimate rotation is dead
        // too: thief and owner cannot be told apart, so both sign in again.
        MvcResult afterwards = refresh(mockMvc, objectMapper, legitimate);
        assertThat(afterwards.getResponse().getStatus()).isEqualTo(401);
        assertThat(afterwards.getResponse().getContentAsString()).contains(GENERIC_401);

        assertThat(events("REFRESH_REUSE_DETECTED")).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("the family revocation survives the 401 thrown straight after it")
    void reuseRevocationIsNotRolledBack() throws Exception {
        User user = newUser();
        String stolen = loginRefreshToken(user);
        String legitimate = rotate(stolen);
        backdateRevocation(stolen, 60);

        assertThat(refresh(mockMvc, objectMapper, stolen).getResponse().getStatus()).isEqualTo(401);

        // Read back from the database in a fresh transaction. A plain
        // @Transactional on refresh() would have rolled this write back along
        // with the UnauthorizedException, and this would still be live.
        RefreshToken successor = row(legitimate);
        assertThat(successor.isRevoked()).isTrue();
        assertThat(successor.getRevokedReason()).isEqualTo(RevocationReason.REUSE_DETECTED);
        assertThat(successor.getRevokedAt()).isNotNull();
        // The replayed token keeps its original reason, so the trail shows
        // which token was rotated and which one was replayed.
        assertThat(row(stolen).getRevokedReason()).isEqualTo(RevocationReason.ROTATED);
    }

    @Test
    @DisplayName("replaying inside the grace window is a plain 401 and the new token keeps working")
    void replayInsideGraceHasNoSideEffects() throws Exception {
        User user = newUser();
        String first = loginRefreshToken(user);
        String second = rotate(first);
        double before = events("REFRESH_REUSE_DETECTED");

        MvcResult replay = refresh(mockMvc, objectMapper, first);
        assertThat(replay.getResponse().getStatus()).isEqualTo(401);
        assertThat(replay.getResponse().getContentAsString()).contains(GENERIC_401);

        assertThat(rotate(second)).isNotBlank();
        assertThat(events("REFRESH_REUSE_DETECTED")).isEqualTo(before);
    }

    @Test
    @DisplayName("two concurrent refreshes with one token: exactly one wins, 20 times running")
    void concurrentRefreshHasExactlyOneWinner() throws Exception {
        // One pass proves nothing about a race. Twenty passes that all come
        // out 1-and-1 are evidence; any pass that comes out 2-and-0 is N1.
        for (int round = 0; round < 20; round++) {
            String token = loginRefreshToken(newUser());
            List<Integer> statuses = raceTwoRefreshes(mockMvc, objectMapper, token);
            assertThat(statuses).as("round %d", round).containsExactlyInAnyOrder(200, 401);
        }
    }

    @Test
    @DisplayName("an unknown or expired token is the same generic 401")
    void unknownAndExpiredLookTheSame() throws Exception {
        MvcResult unknown = refresh(mockMvc, objectMapper, "not-a-real-token");
        assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknown.getResponse().getContentAsString()).contains(GENERIC_401);

        String token = loginRefreshToken(newUser());
        jdbcTemplate.update("UPDATE refresh_tokens SET expires_at = ? WHERE token_hash = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), TokenHasher.sha256Hex(token));
        MvcResult expired = refresh(mockMvc, objectMapper, token);
        assertThat(expired.getResponse().getStatus()).isEqualTo(401);
        assertThat(expired.getResponse().getContentAsString()).contains(GENERIC_401);
    }

    @Test
    @DisplayName("logout stores LOGOUT")
    void logoutStoresReason() throws Exception {
        String token = loginRefreshToken(newUser());
        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", token))))
                .andExpect(status().isOk());

        assertThat(row(token).isRevoked()).isTrue();
        assertThat(row(token).getRevokedReason()).isEqualTo(RevocationReason.LOGOUT);
        assertThat(row(token).getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("logging out with an already-rotated token does not overwrite ROTATED")
    void logoutKeepsRotatedReason() throws Exception {
        String first = loginRefreshToken(newUser());
        rotate(first);
        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", first))))
                .andExpect(status().isOk());

        assertThat(row(first).getRevokedReason()).isEqualTo(RevocationReason.ROTATED);
    }

    @Test
    @DisplayName("a password change stores PASSWORD_CHANGED on every live token")
    void passwordChangeStoresReason() throws Exception {
        User user = newUser();
        JsonNode session = login(mockMvc, objectMapper, user.getEmail(), PASSWORD);
        String other = loginRefreshToken(user);

        mockMvc.perform(post("/api/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", PASSWORD, "newPassword", "NewPassword2"))))
                .andExpect(status().isOk());

        assertThat(row(session.get("refreshToken").asText()).getRevokedReason())
                .isEqualTo(RevocationReason.PASSWORD_CHANGED);
        assertThat(row(other).getRevokedReason()).isEqualTo(RevocationReason.PASSWORD_CHANGED);
    }

    @Test
    @DisplayName("a password reset stores PASSWORD_RESET on every live token")
    void passwordResetStoresReason() throws Exception {
        User user = newUser();
        String token = loginRefreshToken(user);

        String rawResetToken = TokenHasher.generateRawToken();
        passwordResetTokenRepository.save(com.iunu.realestate.entity.PasswordResetToken.builder()
                .user(user)
                .tokenHash(TokenHasher.sha256Hex(rawResetToken))
                .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                .build());

        mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", rawResetToken, "newPassword", "NewPassword2"))))
                .andExpect(status().isOk());

        assertThat(row(token).getRevokedReason()).isEqualTo(RevocationReason.PASSWORD_RESET);
    }
}
