package com.iunu.realestate.security;

import com.iunu.realestate.entity.Role;
import com.iunu.realestate.entity.User;
import com.iunu.realestate.support.IntegrationTest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The access token is the only thing standing between the public internet and
 * every admin endpoint, so each way of faking one gets its own test: a wrong
 * signing key, an edited payload, a stripped signature, an expired token and a
 * foreign issuer must all be rejected outright rather than merely decoded.
 */
@DisplayName("JWT validation")
class JwtValidationTest extends IntegrationTest {

    /** Matches app.jwt.secret in application-test.yml. */
    private static final String TEST_SECRET = "test-only-secret-that-is-at-least-32-bytes-long-for-hmac-sha";
    private static final String ISSUER = "iunu-real-estate-api";

    private static SecretKey keyFor(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private User admin() {
        return createUser("jwt-" + System.nanoTime() + "@iunu.test", "Password1", Role.ADMIN);
    }

    @Test
    @DisplayName("a correctly signed admin token is accepted (control case)")
    void acceptsValidToken() throws Exception {
        mockMvc.perform(get("/api/admin/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(admin())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("rejects a token signed with a different secret - the signature is verified, not just parsed")
    void rejectsForeignSignature() throws Exception {
        User user = admin();
        String forged = Jwts.builder()
                .issuer(ISSUER)
                .subject(user.getEmail())
                .claims(Map.of("uid", user.getId(), "role", "ADMIN"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900_000))
                .signWith(keyFor("a-completely-different-secret-that-is-also-32-bytes-plus"))
                .compact();

        mockMvc.perform(get("/api/admin/projects").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rejects a token whose payload was edited after signing")
    void rejectsTamperedPayload() throws Exception {
        User user = createUser("plain-" + System.nanoTime() + "@iunu.test", "Password1", Role.USER);
        String token = jwtService.generateAccessToken(user.getId(), user.getEmail(), "USER");

        // Re-encode the payload with role=ADMIN, keeping header and signature.
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String escalated = payload.replace("\"role\":\"USER\"", "\"role\":\"ADMIN\"");
        assertThat(escalated).isNotEqualTo(payload);

        String tampered = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(escalated.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        assertThat(jwtService.isValid(tampered)).isFalse();

        mockMvc.perform(get("/api/admin/projects").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rejects a token whose signature segment was swapped for another token's")
    void rejectsSwappedSignature() throws Exception {
        String first = jwtService.generateAccessToken(1L, "a@iunu.test", "ADMIN");
        String second = jwtService.generateAccessToken(2L, "b@iunu.test", "ADMIN");

        String[] firstParts = first.split("\\.");
        String[] secondParts = second.split("\\.");
        String spliced = firstParts[0] + "." + firstParts[1] + "." + secondParts[2];

        assertThat(jwtService.isValid(spliced)).isFalse();

        mockMvc.perform(get("/api/admin/projects").header(HttpHeaders.AUTHORIZATION, "Bearer " + spliced))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rejects an unsigned 'alg: none' style token")
    void rejectsUnsignedToken() throws Exception {
        String[] parts = jwtService.generateAccessToken(1L, "a@iunu.test", "ADMIN").split("\\.");
        String unsigned = parts[0] + "." + parts[1] + ".";

        assertThat(jwtService.isValid(unsigned)).isFalse();

        mockMvc.perform(get("/api/admin/projects").header(HttpHeaders.AUTHORIZATION, "Bearer " + unsigned))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rejects an expired token even though its signature is genuine")
    void rejectsExpiredToken() throws Exception {
        User user = admin();
        long now = System.currentTimeMillis();

        String expired = Jwts.builder()
                .issuer(ISSUER)
                .subject(user.getEmail())
                .claims(Map.of("uid", user.getId(), "role", "ADMIN"))
                .issuedAt(new Date(now - 7_200_000))
                .expiration(new Date(now - 3_600_000)) // an hour ago
                .signWith(keyFor(TEST_SECRET))
                .compact();

        assertThat(jwtService.isValid(expired)).isFalse();

        mockMvc.perform(get("/api/admin/projects").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("rejects a correctly signed token minted for a different issuer")
    void rejectsForeignIssuer() throws Exception {
        User user = admin();
        String otherIssuer = Jwts.builder()
                .issuer("some-other-service")
                .subject(user.getEmail())
                .claims(Map.of("uid", user.getId(), "role", "ADMIN"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900_000))
                .signWith(keyFor(TEST_SECRET))
                .compact();

        assertThat(jwtService.isValid(otherIssuer)).isFalse();

        mockMvc.perform(get("/api/admin/projects").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherIssuer))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a malformed Authorization header is ignored rather than trusted")
    void rejectsMalformedHeader() throws Exception {
        mockMvc.perform(get("/api/admin/projects").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/projects").header(HttpHeaders.AUTHORIZATION, "Basic YWRtaW46YWRtaW4="))
                .andExpect(status().isUnauthorized());
    }
}
