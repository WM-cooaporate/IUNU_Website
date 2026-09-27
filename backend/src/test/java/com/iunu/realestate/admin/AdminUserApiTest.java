package com.iunu.realestate.admin;

import com.iunu.realestate.entity.Role;
import com.iunu.realestate.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("POST /api/admin/users")
class AdminUserApiTest extends IntegrationTest {

    @Test
    @DisplayName("an admin can create another admin, and the hash never leaves the API")
    void adminCreatesAdmin() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "fullName", "Second Admin",
                "email", "Second.Admin@IUNU.test",
                "phone", "+20 100 111 2222",
                "password", "Password1"));

        String response = mockMvc.perform(post("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                // Emails are normalized to lower case so logins are case-insensitive.
                .andExpect(jsonPath("$.email").value("second.admin@iunu.test"))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("password").doesNotContain("$2a$");

        assertThat(userRepository.findByEmailIgnoreCase("second.admin@iunu.test").orElseThrow().getRole())
                .isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("is not reachable without a token or as a non-admin - no public self-registration of admins")
    void requiresAdmin() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "fullName", "Sneaky", "email", "sneaky@iunu.test", "password", "Password1"));

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, userBearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findByEmailIgnoreCase("sneaky@iunu.test")).isEmpty();
    }

    @Test
    @DisplayName("rejects a password under 8 characters")
    void rejectsShortPassword() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "fullName", "Weak", "email", "weak@iunu.test", "password", "Pass1"));

        mockMvc.perform(post("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));

        assertThat(userRepository.findByEmailIgnoreCase("weak@iunu.test")).isEmpty();
    }

    @Test
    @DisplayName("rejects a duplicate email")
    void rejectsDuplicateEmail() throws Exception {
        createUser("taken@iunu.test", "Password1", Role.ADMIN);

        String body = objectMapper.writeValueAsString(Map.of(
                "fullName", "Dup", "email", "taken@iunu.test", "password", "Password1"));

        mockMvc.perform(post("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
}
