package com.iunu.realestate.security;

import com.iunu.realestate.entity.Role;
import com.iunu.realestate.entity.User;
import com.iunu.realestate.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The rate limiter, in isolation.
 *
 * <p>It is off for the rest of the suite ({@code app.security.rate-limit.enabled:
 * false} in application-test.yml) because every test shares one application
 * context: a limiter counting across the whole run would make failures depend
 * on which tests happened to go first. This class turns it back on under its own
 * property set, and {@code @DirtiesContext} throws the context away afterwards
 * so no later test inherits spent buckets.
 *
 * <p>The bucket store is deliberately tiny here (16 entries) so eviction is
 * observable without sending a hundred thousand requests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.security.rate-limit.enabled=true",
        "app.security.rate-limit.max-tracked-clients=16",
        "app.client-ip.mode=x-forwarded-for",
        "app.client-ip.trusted-proxy-hops=1",
        // Its own H2 database, not the suite's. @DirtiesContext closes this
        // context, and ddl-auto=create-drop drops the schema on the way out -
        // against the shared URL that would leave every test that runs
        // afterwards talking to an empty database.
        "spring.datasource.url=jdbc:h2:mem:iunu-ratelimit;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;"
                + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Rate limiting")
class RateLimitingFilterTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private RateLimitingFilter rateLimitingFilter;

    /**
     * Every test uses its own synthetic client address, so the buckets one
     * test spends are not the buckets the next one reads.
     */
    private static String uniqueIp() {
        return "203.0.113." + (System.nanoTime() % 200 + 10);
    }

    private String bearerForFreshAdmin() {
        User admin = userRepository.save(User.builder()
                .fullName("Limiter Admin")
                .email("limiter-" + System.nanoTime() + "@iunu.test")
                .phone("+20 100 000 0000")
                .password(passwordEncoder.encode("Password1"))
                .role(Role.ADMIN)
                .build());
        return "Bearer " + jwtService.generateAccessToken(admin.getId(), admin.getEmail(), admin.getRole().name());
    }

    @Test
    @DisplayName("the 11th login attempt in a minute is refused with 429 and Retry-After")
    void loginLimitIsTenPerMinute() throws Exception {
        String ip = uniqueIp();

        for (int attempt = 1; attempt <= 10; attempt++) {
            mockMvc.perform(badLogin(ip))
                    .andExpect(status().isUnauthorized());
        }

        MvcResult refused = mockMvc.perform(badLogin(ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andReturn();

        String retryAfter = refused.getResponse().getHeader(HttpHeaders.RETRY_AFTER);
        assertThat(retryAfter).isNotNull();
        // A client told to retry in 0 seconds retries immediately, which makes
        // the flood worse rather than better.
        assertThat(Integer.parseInt(retryAfter)).isGreaterThanOrEqualTo(1);
    }

    /**
     * The attack the ClientIpResolver exists to stop: rotate a fake leftmost
     * X-Forwarded-For entry and land in a fresh bucket every request. The
     * resolver reads the right-hand hop, which the caller cannot set, so all of
     * these share one bucket and the limit still bites.
     */
    @Test
    @DisplayName("a spoofed leftmost X-Forwarded-For does not reset the bucket")
    void spoofedForwardedForDoesNotBypassTheLimit() throws Exception {
        String realIp = uniqueIp();

        for (int attempt = 1; attempt <= 10; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                            .header("X-Forwarded-For", "1.2.3." + attempt + ", " + realIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(badCredentials()))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "9.9.9.9, " + realIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badCredentials()))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("the public read limit refuses a sustained scraping loop")
    void publicReadLimitApplies() throws Exception {
        String ip = uniqueIp();
        boolean refused = false;

        // The limit is 300/min with a greedy refill; 400 requests in a tight
        // loop cannot all be served however the refill lands.
        for (int request = 1; request <= 400 && !refused; request++) {
            int status = mockMvc.perform(get("/api/properties").header("X-Forwarded-For", ip))
                    .andReturn().getResponse().getStatus();
            refused = status == 429;
        }

        assertThat(refused).as("a 400-request loop should hit the 300/min public limit").isTrue();
    }

    /**
     * Per user, not per IP. Several admins behind one office NAT must not share
     * an allowance - and, in the other direction, a stolen token must not get a
     * fresh allowance by changing address.
     */
    @Test
    @DisplayName("the admin limit keys on the authenticated user, not the address")
    void adminLimitIsPerUser() throws Exception {
        String bearer = bearerForFreshAdmin();
        boolean refused = false;

        // Same user, a different address every request. If the key were the IP,
        // each of these would get its own bucket and nothing would be refused.
        for (int request = 1; request <= 200 && !refused; request++) {
            int status = mockMvc.perform(get("/api/properties/admin")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .header("X-Forwarded-For", "198.51.100." + (request % 250)))
                    .andReturn().getResponse().getStatus();
            refused = status == 429;
        }

        assertThat(refused).as("120/min per user should refuse a 200-request loop").isTrue();
    }

    @Test
    @DisplayName("a different admin is unaffected by another admin's spent bucket")
    void adminBucketsAreIndependent() throws Exception {
        String firstAdmin = bearerForFreshAdmin();
        for (int request = 1; request <= 130; request++) {
            mockMvc.perform(get("/api/properties/admin").header(HttpHeaders.AUTHORIZATION, firstAdmin));
        }

        mockMvc.perform(get("/api/properties/admin")
                        .header(HttpHeaders.AUTHORIZATION, bearerForFreshAdmin()))
                .andExpect(status().isOk());
    }

    /**
     * The reason the store is a Caffeine cache and not a ConcurrentHashMap: a
     * flood from many addresses must not grow it without bound. With a cap of
     * 16, the first address's bucket is gone by the time 200 others have been
     * seen - which is the eviction working.
     */
    @Test
    @DisplayName("the bucket store is bounded and evicts under pressure")
    void bucketStoreIsBounded() throws Exception {
        String firstIp = "192.0.2.1";

        // Spend the first client's login allowance.
        for (int attempt = 1; attempt <= 10; attempt++) {
            mockMvc.perform(badLogin(firstIp));
        }
        mockMvc.perform(badLogin(firstIp)).andExpect(status().isTooManyRequests());

        // Now push far more distinct clients through than the store can hold.
        for (int client = 0; client < 60; client++) {
            mockMvc.perform(badLogin("198.18." + (client / 250) + "." + (client % 250)));
        }

        // Caffeine's eviction is asynchronous and size-based rather than
        // strictly LRU, so the assertion is on the store's bound, not on which
        // exact entry went: an unbounded map would still be holding all 201.
        assertThat(bucketStoreSize()).isLessThanOrEqualTo(32);
    }

    /** Reaches into the filter's login store to assert it stayed bounded. */
    private long bucketStoreSize() throws Exception {
        var field = RateLimitingFilter.class.getDeclaredField("loginBuckets");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        com.github.benmanes.caffeine.cache.Cache<String, ?> store =
                (com.github.benmanes.caffeine.cache.Cache<String, ?>) field.get(rateLimitingFilter);
        store.cleanUp();
        return store.estimatedSize();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder badLogin(String ip) {
        return post("/api/auth/login")
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content(badCredentials());
    }

    /**
     * A throwaway address that belongs to no account. Never a real admin
     * email: repeated failures against one would lock it, which is a different
     * defence and a destructive side effect for a test to have.
     */
    private static String badCredentials() {
        return "{\"email\":\"nobody-" + System.nanoTime() + "@iunu.test\",\"password\":\"WrongPassword1\"}";
    }
}
