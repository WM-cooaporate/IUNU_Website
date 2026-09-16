package com.iunu.realestate.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The optional origin lock.
 *
 * <p>Two behaviours have to hold, and they pull in opposite directions: with no
 * secret configured the filter must be completely invisible (this is the
 * default, and a filter that quietly started refusing traffic would take the
 * API down), and with one configured it must refuse everything that did not
 * come through the edge - <em>except</em> the health probe, which the hosting
 * platform polls from inside its own network and gates every deploy on.
 */
@DisplayName("Edge shared-secret filter")
class EdgeSecretFilterTest {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @DisplayName("with no secret configured (the default)")
    class Disabled {

        @Autowired private MockMvc mockMvc;

        @Test
        @DisplayName("requests pass through untouched")
        void doesNothing() throws Exception {
            mockMvc.perform(get("/api/properties")).andExpect(status().isOk());
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @TestPropertySource(properties = {
            "app.edge.shared-secret=s3cr3t-from-cloudflare",
            // Its own H2 database: @DirtiesContext closes this context and
            // ddl-auto=create-drop drops the schema, which against the shared
            // URL would empty the database for every later test.
            "spring.datasource.url=jdbc:h2:mem:iunu-edge;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;"
                    + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
    })
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
    @DisplayName("with a secret configured")
    class Enabled {

        @Autowired private MockMvc mockMvc;

        @Test
        @DisplayName("a request without the header is refused")
        void refusesRequestsWithoutTheHeader() throws Exception {
            mockMvc.perform(get("/api/properties")).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("a request with the wrong secret is refused")
        void refusesTheWrongSecret() throws Exception {
            mockMvc.perform(get("/api/properties").header(EdgeSecretFilter.HEADER, "guess"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("a request carrying the secret is served")
        void allowsTheCorrectSecret() throws Exception {
            mockMvc.perform(get("/api/properties")
                            .header(EdgeSecretFilter.HEADER, "s3cr3t-from-cloudflare"))
                    .andExpect(status().isOk());
        }

        /**
         * The exemption that stops this feature from breaking every deploy: the
         * platform's health check never goes through Cloudflare, so gating it
         * would mark the release unhealthy and roll it back.
         */
        @Test
        @DisplayName("the health probe stays reachable without the header")
        void healthProbeIsExempt() throws Exception {
            mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }
    }
}
