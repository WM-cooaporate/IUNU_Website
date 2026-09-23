package com.iunu.realestate.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * N6: the prod profile's forward-headers handling let a caller choose their
 * own rate-limit bucket with a fake X-Forwarded-For. This runs the same trust
 * setup as production behind one proxy - x-forwarded-for mode, the proxy
 * named in TRUSTED_PROXIES, forward-headers-strategy none - and proves the
 * bypass is closed.
 *
 * <p>Fails with server.forward-headers-strategy=framework: Spring's
 * ForwardedHeaderFilter then rewrites the client address to the leftmost,
 * caller-typed entry before the rate limiter sees it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "server.forward-headers-strategy=none",
        "app.security.rate-limit.enabled=true",
        "app.client-ip.mode=x-forwarded-for",
        "app.client-ip.trusted-proxy-hops=1",
        // MockMvc's peer address: the "proxy".
        "app.client-ip.trusted-proxies=127.0.0.1",
        "spring.datasource.url=jdbc:h2:mem:iunu-forwarded;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;"
                + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Forwarded headers (N6)")
class ForwardedHeadersBypassTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("a rotating fake X-Forwarded-For does not buy a fresh rate-limit bucket")
    void spoofedForwardedForDoesNotResetTheBucket() throws Exception {
        int tooMany = 0;
        for (int i = 0; i < 400 && tooMany == 0; i++) {
            // What the proxy forwards: the caller's fake entry on the left,
            // the real client address the proxy appended on the right.
            int status = mockMvc.perform(get("/api/properties")
                            .header("X-Forwarded-For", "1.2.3." + (i % 250) + ", 198.51.100.20"))
                    .andReturn().getResponse().getStatus();
            if (status == 429) {
                tooMany++;
            }
        }
        assertThat(tooMany).as("the public limit (300/min) must still bite").isPositive();
    }

    @Test
    @DisplayName("X-Forwarded-Proto: https from the trusted proxy makes the request secure, so HSTS is sent")
    void httpsFromTrustedProxyIsHonoured() throws Exception {
        String hsts = mockMvc.perform(get("/actuator/health").header("X-Forwarded-Proto", "https"))
                .andReturn().getResponse().getHeader("Strict-Transport-Security");
        assertThat(hsts).startsWith("max-age=");
    }

    @Test
    @DisplayName("X-Forwarded-Proto from a peer that is not a trusted proxy is ignored")
    void httpsFromUntrustedPeerIsIgnored() throws Exception {
        String hsts = mockMvc.perform(get("/actuator/health")
                        .header("X-Forwarded-Proto", "https")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.50");
                            return request;
                        }))
                .andReturn().getResponse().getHeader("Strict-Transport-Security");
        assertThat(hsts).isNull();
    }
}
