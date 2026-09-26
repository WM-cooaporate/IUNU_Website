package com.iunu.realestate.security;

import com.iunu.realestate.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.iunu.realestate.entity.Property;
import com.iunu.realestate.entity.PropertyType;
import com.iunu.realestate.repository.PropertyRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
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
            "GET,    /api/admin/audit-log",
            // Operational data: request rates, cache hit ratios, pool depth,
            // the JVM's own configuration. Only /actuator/health is anonymous.
            "GET,    /actuator/metrics",
            "GET,    /actuator/prometheus",
            "GET,    /actuator/caches",
            "GET,    /actuator/info",
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
            "GET,    /api/admin/audit-log",
            // Operational data: request rates, cache hit ratios, pool depth,
            // the JVM's own configuration. Only /actuator/health is anonymous.
            "GET,    /actuator/metrics",
            "GET,    /actuator/prometheus",
            "GET,    /actuator/caches",
            "GET,    /actuator/info",
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
        // The platform polls this on every deploy and gates the release on it.
        mockMvc.perform(json("GET", "/actuator/health")).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------------
    // Path and verb tricks. The same cases load-tests/attack.js sends at the
    // lab, kept here so they run on every build rather than once a week.
    // ---------------------------------------------------------------------

    @Autowired private PropertyRepository propertyRepository;

    /** Anything else - above all a 200 - means a trick reached a handler it should not have. */
    private static final Set<Integer> REFUSALS = Set.of(400, 401, 403, 404, 405);

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/api//admin/users",
            "/api/admin/./users",
            "/api/Admin/users",
            "/api/admin/users;x=1",
            "/api/admin%2fusers",
            "/api/admin%2Fusers",
            "/api/properties/admin/..%2f..%2fadmin/users",
            "/api/properties/..;/admin/users",
            "/api/admin/users/",
            "/api/admin/users%00",
    })
    @DisplayName("path tricks never reach an admin handler, anonymously or as a USER")
    void pathTricksAreRefused(String rawPath) throws Exception {
        URI uri = URI.create("http://localhost" + rawPath);
        int anonymous = mockMvc.perform(request(HttpMethod.GET, uri)).andReturn().getResponse().getStatus();
        int asUser = mockMvc.perform(request(HttpMethod.GET, uri).header(HttpHeaders.AUTHORIZATION, userBearer()))
                .andReturn().getResponse().getStatus();

        assertThat(anonymous).as("anonymous %s", rawPath).isIn(REFUSALS);
        assertThat(asUser).as("USER %s", rawPath).isIn(REFUSALS);
    }

    @Test
    @DisplayName("TRACE is never answered with a 200")
    void traceIsRefused() throws Exception {
        int status = mockMvc.perform(request(HttpMethod.TRACE, "/api/properties")).andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(200);
        int admin = mockMvc.perform(request(HttpMethod.TRACE, "/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, adminBearer())).andReturn().getResponse().getStatus();
        assertThat(admin).isNotEqualTo(200);
    }

    @Test
    @DisplayName("OPTIONS without an Origin does not bypass admin authorization")
    void optionsWithoutOriginIsNotABypass() throws Exception {
        mockMvc.perform(request(HttpMethod.OPTIONS, "/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("method-override tricks cannot turn a POST into a DELETE, even for an admin")
    void methodOverrideIsIgnored() throws Exception {
        Property property = propertyRepository.save(Property.builder()
                .title("Override target").type(PropertyType.RESIDENTIAL).published(true).build());
        String path = "/api/properties/" + property.getId();

        for (String bearer : new String[]{null, userBearer(), adminBearer()}) {
            MockHttpServletRequestBuilder hidden = request(HttpMethod.POST, path + "?_method=DELETE")
                    .param("_method", "DELETE");
            MockHttpServletRequestBuilder header = request(HttpMethod.POST, path)
                    .header("X-HTTP-Method-Override", "DELETE")
                    .header("X-HTTP-Method", "DELETE")
                    .header("X-Method-Override", "DELETE");
            if (bearer != null) {
                hidden.header(HttpHeaders.AUTHORIZATION, bearer);
                header.header(HttpHeaders.AUTHORIZATION, bearer);
            }
            assertThat(mockMvc.perform(hidden).andReturn().getResponse().getStatus()).isIn(REFUSALS);
            assertThat(mockMvc.perform(header).andReturn().getResponse().getStatus()).isIn(REFUSALS);
        }

        assertThat(propertyRepository.existsById(property.getId())).as("the property still exists").isTrue();
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
