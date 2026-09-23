package com.iunu.realestate.security;

import com.iunu.realestate.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@DisplayName("X-Request-Id")
class RequestIdFilterTest extends IntegrationTest {

    private static final String UUID_SHAPE = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    @Test
    @DisplayName("a well-formed incoming id is echoed back")
    void wellFormedIdIsKept() throws Exception {
        String id = "3f2c1b9e-8d4a-4c21-9f0e-2b7a6c5d4e3f";
        mockMvc.perform(get("/api/properties").header(RequestIdFilter.HEADER, id))
                .andExpect(header().string(RequestIdFilter.HEADER, id));
    }

    @Test
    @DisplayName("a missing or malformed id is replaced, never echoed")
    void malformedIdIsReplaced() throws Exception {
        String hostile = "abc\r\nINFO forged line";
        String echoed = mockMvc.perform(get("/api/properties").header(RequestIdFilter.HEADER, hostile))
                .andReturn().getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(echoed).matches(UUID_SHAPE);

        String generated = mockMvc.perform(get("/api/properties"))
                .andReturn().getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(generated).matches(UUID_SHAPE);
    }

    @Test
    @DisplayName("even a refused request carries an id")
    void refusedRequestsAreTraceable() throws Exception {
        String id = mockMvc.perform(get("/api/admin/users")).andReturn().getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(id).matches(UUID_SHAPE);
    }

    @Test
    @DisplayName("the browser is allowed to read it")
    void exposedToCors() throws Exception {
        mockMvc.perform(get("/api/properties").header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        org.hamcrest.Matchers.containsString("X-Request-Id")));
    }
}
