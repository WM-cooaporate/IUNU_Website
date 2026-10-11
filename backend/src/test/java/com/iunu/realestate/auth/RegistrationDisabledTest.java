package com.iunu.realestate.auth;

import com.iunu.realestate.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With the default configuration, public self-registration does not exist:
 * POST /api/auth/register is a 404 whatever the body, and no account is
 * created. Accounts come from the admin bootstrap and POST /api/admin/users.
 * The endpoint's own behaviour, for when app.auth.registration-enabled is
 * switched on, is covered by InputHandlingTest and ErrorHandlingIntegrationTest.
 */
@DisplayName("Registration: off by default")
class RegistrationDisabledTest extends IntegrationTest {

    @Test
    @DisplayName("a valid registration is a 404 and creates no user")
    void validRegistrationIsNotFound() throws Exception {
        String email = "signup-" + System.nanoTime() + "@iunu.test";
        long usersBefore = userRepository.count();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"fullName":"Visitor","email":"%s","phone":"+20 100 000 0000",
                                  "password":"Password1"}
                                 """.formatted(email)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"));

        assertThat(userRepository.findByEmailIgnoreCase(email)).isEmpty();
        assertThat(userRepository.count()).isEqualTo(usersBefore);
    }

    /** Answered before the body is read, so a bad body does not reveal the endpoint with a 400. */
    @Test
    @DisplayName("an invalid or malformed body is also a 404, not a 400")
    void invalidBodyIsNotFound() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"a\": "))
                .andExpect(status().isNotFound());
    }
}
