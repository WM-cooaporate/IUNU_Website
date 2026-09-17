package com.iunu.realestate.security;

import com.iunu.realestate.entity.Role;
import com.iunu.realestate.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * How the API answers input it did not expect.
 *
 * <p>Two separate concerns. The first is privilege: a field the caller invents
 * must not become state. The second is hygiene - a bad parameter is a 400, not
 * a 500, and the body says nothing about what is behind it. A 500 with an
 * exception name is a free map of the implementation, and a stack trace in a
 * response is a free map of the dependency versions.
 */
@DisplayName("Input handling")
class InputHandlingTest extends IntegrationTest {

    /**
     * Mass assignment. RegisterRequest has no role field, so an extra one in
     * the JSON has nowhere to bind - but that is a property of the DTO, and
     * DTOs get fields added. This is the test that notices if one ever does.
     */
    @Test
    @DisplayName("registering with \"role\":\"ADMIN\" produces a USER")
    void registrationCannotGrantAdmin() throws Exception {
        String email = "escalate-" + System.nanoTime() + "@iunu.test";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"fullName":"Mallory","email":"%s","phone":"+20 100 000 0000",
                                  "password":"Password1","role":"ADMIN","accountLocked":false,
                                  "authorities":["ROLE_ADMIN"]}
                                 """.formatted(email)))
                .andExpect(status().isCreated());

        assertThat(userRepository.findByEmailIgnoreCase(email))
                .get()
                .extracting(user -> user.getRole())
                .isEqualTo(Role.USER);
    }

    /**
     * Pageable binds ?sort= straight onto entity property paths. An unknown one
     * raises deep inside Spring Data and, unmapped, becomes a 500 - which both
     * looks like a fault and tells the caller they found something. Varying the
     * name and watching the status change is a field oracle.
     */
    @Test
    @DisplayName("sorting by a field that does not exist is a 400, not a 500")
    void unknownSortFieldIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/properties").param("sort", "password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    @DisplayName("an invalid enum value is a 400 with no internal detail")
    void invalidEnumIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/properties").param("type", "NOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    @DisplayName("malformed JSON is a 400 with no parser detail")
    void malformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    /**
     * A user record must never serialise its password hash, anywhere. The
     * dashboard renders whatever the API returns, so a leak here reaches a
     * browser and then a browser cache.
     */
    @Test
    @DisplayName("no user-shaped response ever contains a password")
    void userResponsesNeverIncludeAPassword() throws Exception {
        String body = mockMvc.perform(get("/api/admin/users")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("password").doesNotContain("passwordHash").doesNotContain("$2a$");
    }
}
