package com.iunu.realestate.admin;

import com.iunu.realestate.entity.Role;
import com.iunu.realestate.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pins the login contract the admin dashboard is built against: the token,
 * its ISO-8601 expiry, and the absence of anything password-shaped.
 */
@DisplayName("POST /api/auth/login")
class LoginResponseShapeTest extends IntegrationTest {

    @Test
    @DisplayName("returns a bearer token with an ISO-8601 expiresAt and the user, never the hash")
    void returnsTokenAndExpiry() throws Exception {
        createUser("login-shape@iunu.test", "Password1", Role.ADMIN);

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "login-shape@iunu.test",
                                "password", "Password1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").isNumber())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("login-shape@iunu.test"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("$2a$");

        Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
        Instant expiresAt = Instant.parse((String) parsed.get("expiresAt"));
        assertThat(expiresAt).isAfter(Instant.now());
    }

    @Test
    @DisplayName("401s on a wrong password with a message that does not reveal whether the email exists")
    void genericFailureMessage() throws Exception {
        createUser("real@iunu.test", "Password1", Role.ADMIN);

        String wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "real@iunu.test", "password", "WrongPassword1"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "nobody@iunu.test", "password", "WrongPassword1"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(messageOf(wrongPassword)).isEqualTo(messageOf(unknownEmail));
    }

    private String messageOf(String json) throws Exception {
        return (String) objectMapper.readValue(json, Map.class).get("message");
    }
}
