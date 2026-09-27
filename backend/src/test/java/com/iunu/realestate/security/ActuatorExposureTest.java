package com.iunu.realestate.security;

import com.iunu.realestate.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What /actuator gives away, and to whom.
 *
 * <p>The health probe has to be anonymous - the hosting platform polls it on
 * every deploy and gates the release on it. Everything else is operational
 * detail: request rates, cache hit ratios, connection-pool depth, the JVM's own
 * configuration. Useful to an operator, and equally useful to someone deciding
 * where this app is weakest.
 */
// @SpringBootTest disables metrics export by default, which takes
// /actuator/prometheus out of the routing table and would make this class
// assert the wrong thing. This turns it back on so the endpoints behave the way
// they do in production - which is the only version worth testing.
@AutoConfigureObservability
// Its own H2 database, because the property above gives this class a distinct
// application context and ddl-auto=create-drop would otherwise have two live
// contexts recreating the same schema underneath each other.
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:iunu-actuator;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;"
                + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@DisplayName("Actuator exposure")
class ActuatorExposureTest extends IntegrationTest {

    @Test
    @DisplayName("health is anonymous, 200, and reveals nothing but the status")
    void healthIsPublicButOpaque() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                // show-details: never. No component names, no database host, no
                // error text - "UP" and nothing else.
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    @DisplayName("the liveness and readiness probes are anonymous too")
    void probesArePublic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("metrics, prometheus and caches require an ADMIN token")
    void operationalEndpointsAreAdminOnly() throws Exception {
        for (String path : new String[]{"/actuator/metrics", "/actuator/prometheus", "/actuator/caches",
                "/actuator/info"}) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, userBearer()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("an admin can read the metrics an incident needs")
    void adminCanReadMetrics() throws Exception {
        String bearer = adminBearer();

        mockMvc.perform(get("/actuator/prometheus").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
        // The counters the DDoS runbook tells an operator to look at. A rename
        // would break the runbook silently.
        mockMvc.perform(get("/actuator/metrics/iunu.ratelimit.rejected")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/metrics/hikaricp.connections.active")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
    }

    /**
     * recordStats() on the Caffeine builder is what publishes these. Without
     * it the metric does not exist and a load test cannot tell a warm cache
     * from a cold one.
     */
    @Test
    @DisplayName("cache hit rates are visible to an admin")
    void cacheStatsAreExposed() throws Exception {
        // Populate the cache so the meter is registered.
        mockMvc.perform(get("/api/properties")).andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics/cache.gets")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk());
    }

    /**
     * An endpoint that is not in management.endpoints.web.exposure.include must
     * not become reachable just because someone holds an admin token - it
     * should not be routed at all.
     */
    @Test
    @DisplayName("an unexposed endpoint is not reachable even for an admin")
    void unexposedEndpointsStayUnreachable() throws Exception {
        mockMvc.perform(get("/actuator/env").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/heapdump").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNotFound());
    }
}
