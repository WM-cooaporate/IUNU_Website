package com.iunu.realestate.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.iunu.realestate.entity.PasswordResetToken;
import com.iunu.realestate.entity.Role;
import com.iunu.realestate.entity.User;
import com.iunu.realestate.repository.PasswordResetTokenRepository;
import com.iunu.realestate.security.events.SecurityEventType;
import com.iunu.realestate.security.events.SecurityEvents;
import com.iunu.realestate.support.IntegrationTest;
import com.iunu.realestate.support.LogCapture;
import com.iunu.realestate.util.TokenHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Drives every event this test profile can reach through real requests, with
 * real secrets in them, and checks the SECURITY log for every one of those
 * secrets. The events that need another profile (RATE_LIMITED,
 * EDGE_SECRET_REJECTED), or that no request can currently trigger
 * (ACCOUNT_LOCKED - see N5 in the audit addendum - and REFRESH_RACE_LOST,
 * which needs a lost race), are recorded directly with the same arguments
 * their production call sites pass.
 */
@DisplayName("SECURITY log redaction")
class SecurityLogRedactionTest extends IntegrationTest {

    private static final String PASSWORD = "Sup3rSecretPass";

    @Autowired private SecurityEvents securityEvents;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    /** A fresh client address per run, so another test's sampling cannot swallow these lines. */
    private final String ip = "10." + ThreadLocalRandom.current().nextInt(1, 250) + "."
            + ThreadLocalRandom.current().nextInt(1, 250) + "." + ThreadLocalRandom.current().nextInt(1, 250);

    private MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder builder) {
        RequestPostProcessor address = request -> {
            request.setRemoteAddr(ip);
            return request;
        };
        return builder.with(address);
    }

    private JsonNode login(String email, String password) throws Exception {
        String body = mockMvc.perform(from(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    @Test
    @DisplayName("no raw email, token, password or Bearer string ever reaches the SECURITY log")
    void securityLogCarriesNoSecrets() throws Exception {
        String email = "redaction-" + System.nanoTime() + "@iunu-eg.com";
        User admin = createUser(email, PASSWORD, Role.ADMIN);
        List<String> secrets = new ArrayList<>(List.of(email, PASSWORD, "Bearer"));

        try (LogCapture capture = new LogCapture(SecurityEvents.LOGGER_NAME)) {
            // LOGIN_FAILED
            login(email, "wrong-" + PASSWORD);
            // LOGIN_SUCCEEDED_ADMIN + ADMIN_LOGIN_NEW_IP
            JsonNode session = login(email, PASSWORD);
            String access = session.get("accessToken").asText();
            String refresh = session.get("refreshToken").asText();
            secrets.add(access);
            secrets.add(refresh);

            // REFRESH_REUSE_DETECTED
            String rotated = objectMapper.readTree(mockMvc.perform(from(post("/api/auth/refresh"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("refreshToken", refresh))))
                    .andReturn().getResponse().getContentAsString()).get("refreshToken").asText();
            secrets.add(rotated);
            jdbcTemplate.update("UPDATE refresh_tokens SET revoked_at = ? WHERE token_hash = ?",
                    java.sql.Timestamp.from(Instant.now().minusSeconds(60)), TokenHasher.sha256Hex(refresh));
            mockMvc.perform(from(post("/api/auth/refresh")).contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("refreshToken", refresh))));

            // PASSWORD_RESET_REQUESTED
            mockMvc.perform(from(post("/api/auth/forgot-password")).contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("email", email))));

            // PASSWORD_RESET_COMPLETED
            String rawReset = TokenHasher.generateRawToken();
            secrets.add(rawReset);
            passwordResetTokenRepository.save(PasswordResetToken.builder().user(admin)
                    .tokenHash(TokenHasher.sha256Hex(rawReset))
                    .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES)).build());
            String resetPassword = "Reset" + PASSWORD;
            secrets.add(resetPassword);
            mockMvc.perform(from(post("/api/auth/reset-password")).contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("token", rawReset, "newPassword", resetPassword))));

            // PASSWORD_CHANGED
            String fresh = login(email, resetPassword).get("accessToken").asText();
            secrets.add(fresh);
            String changedPassword = "Changed" + PASSWORD;
            secrets.add(changedPassword);
            mockMvc.perform(from(post("/api/auth/change-password")).header(HttpHeaders.AUTHORIZATION, "Bearer " + fresh)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                            "currentPassword", resetPassword, "newPassword", changedPassword))));

            // UPLOAD_REJECTED - an SVG declared as PNG, with a hostile filename
            String adminBearer = bearerFor(admin);
            secrets.add(adminBearer.substring("Bearer ".length()));
            mockMvc.perform(from(multipart("/api/properties/images")
                    .file(new MockMultipartFile("files", "../../evil.png", "image/png",
                            "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8))))
                    .header(HttpHeaders.AUTHORIZATION, adminBearer));
            secrets.add("evil.png");

            // ACCESS_DENIED (403 and 401)
            String userBearer = userBearer();
            secrets.add(userBearer.substring("Bearer ".length()));
            mockMvc.perform(from(get("/api/admin/users")).header(HttpHeaders.AUTHORIZATION, userBearer));
            // TOKEN_INVALID
            String forged = access.substring(0, access.length() - 2) + "xx";
            secrets.add(forged);
            mockMvc.perform(from(get("/api/admin/users")).header(HttpHeaders.AUTHORIZATION, "Bearer " + forged));

            // The rest, with the arguments their call sites use.
            securityEvents.record(SecurityEventType.ACCOUNT_LOCKED, admin.getId(), email, ip, Map.of("attempts", "5"));
            securityEvents.record(SecurityEventType.REFRESH_RACE_LOST, admin.getId(), email, ip, Map.of("tokenId", "1"));
            securityEvents.record(SecurityEventType.RATE_LIMITED, null, null, ip,
                    Map.of("bucket", "login", "method", "POST", "path", "/api/auth/login"));
            securityEvents.record(SecurityEventType.EDGE_SECRET_REJECTED, null, null, ip,
                    Map.of("headerPresent", "false", "path", "/api/properties"));

            List<String> lines = capture.messages();
            Set<SecurityEventType> seen = EnumSet.noneOf(SecurityEventType.class);
            for (SecurityEventType type : SecurityEventType.values()) {
                if (lines.stream().anyMatch(line -> line.contains("event=" + type.name() + " "))) {
                    seen.add(type);
                }
            }
            assertThat(seen).as("every event type produced a line").containsExactlyInAnyOrder(SecurityEventType.values());

            for (String line : lines) {
                for (String secret : secrets) {
                    assertThat(line).as("SECURITY line must not contain a secret").doesNotContain(secret);
                }
            }
            assertThat(String.join("\n", lines)).contains("r***@iunu-eg.com");
        }
    }
}
