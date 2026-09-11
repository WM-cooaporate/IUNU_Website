package com.iunu.realestate.security;

import com.iunu.realestate.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every administrative endpoint, swept twice: anonymous must get 401 and an
 * authenticated non-admin must get 403. This is the layer that catches a new
 * endpoint being added without the matching authorization rule.
 *
 * /api/properties/admin/** is in the list on purpose: it sits under the
 * "GET /api/properties/**" public prefix, so it is the easiest place in the
 * app to accidentally publish every unpublished row.
 */
@DisplayName("Admin endpoint authorization")
class AdminEndpointAuthorizationTest extends IntegrationTest {

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "GET,    /api/admin/projects",
            "GET,    /api/admin/projects/1",
            "POST,   /api/admin/projects",
            "PUT,    /api/admin/projects/1",
            "DELETE, /api/admin/projects/1",
            "GET,    /api/admin/users",
            "POST,   /api/admin/users",
            "GET,    /api/admin/contacts",
            "PATCH,  /api/admin/contacts/1/handled",
            "GET,    /api/admin/quotes",
            "PATCH,  /api/admin/quotes/1/handled",
            "GET,    /api/properties/admin",
            "GET,    /api/properties/admin/1",
            "POST,   /api/properties",
            "PUT,    /api/properties/1",
            "DELETE, /api/properties/1",
            "POST,   /api/properties/images",
            "POST,   /api/admin/translations/preview",
            "POST,   /api/admin/translations/properties/backfill",
    })
    @DisplayName("401s with no token")
    void requiresAuthentication(String method, String path) throws Exception {
        mockMvc.perform(json(method, path)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "GET,    /api/admin/projects",
            "GET,    /api/admin/projects/1",
            "POST,   /api/admin/projects",
            "PUT,    /api/admin/projects/1",
            "DELETE, /api/admin/projects/1",
            "GET,    /api/admin/users",
            "POST,   /api/admin/users",
            "GET,    /api/admin/contacts",
            "PATCH,  /api/admin/contacts/1/handled",
            "GET,    /api/admin/quotes",
            "PATCH,  /api/admin/quotes/1/handled",
            "GET,    /api/properties/admin",
            "GET,    /api/properties/admin/1",
            "POST,   /api/properties",
            "PUT,    /api/properties/1",
            "DELETE, /api/properties/1",
            "POST,   /api/properties/images",
            "POST,   /api/admin/translations/preview",
            "POST,   /api/admin/translations/properties/backfill",
    })
    @DisplayName("403s for an authenticated non-admin")
    void forbidsNonAdmins(String method, String path) throws Exception {
        mockMvc.perform(json(method, path).header(HttpHeaders.AUTHORIZATION, userBearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the public endpoints the site actually uses stay open")
    void publicEndpointsRemainPublic() throws Exception {
        mockMvc.perform(json("GET", "/api/projects")).andExpect(status().isOk());
        mockMvc.perform(json("GET", "/api/properties")).andExpect(status().isOk());
    }

    /**
     * A body is attached to every write so the request reaches the security
     * layer rather than being rejected earlier for a missing/unreadable body -
     * a 400 there would hide the authorization result this test is asserting.
     */
    private static MockHttpServletRequestBuilder json(String method, String path) {
        MockHttpServletRequestBuilder builder =
                request(HttpMethod.valueOf(method.trim()), path.trim())
                        .contentType(MediaType.APPLICATION_JSON);
        return builder.content("{\"title\":\"x\",\"type\":\"RESIDENTIAL\"}");
    }
}
