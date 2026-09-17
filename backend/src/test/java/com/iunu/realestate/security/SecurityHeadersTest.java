package com.iunu.realestate.security;

import com.iunu.realestate.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The response headers. Each one here is load-bearing for a specific attack, so
 * a future refactor of the headers() block has to keep them rather than
 * rediscover why they were added.
 */
@DisplayName("Security and caching headers")
class SecurityHeadersTest extends IntegrationTest {

    @Test
    @DisplayName("every API response carries the baseline security headers")
    void baselineSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/properties"))
                .andExpect(status().isOk())
                // Stops a browser from re-interpreting a JSON response as HTML
                // or script, which is what turns a reflected value into XSS.
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy",
                        containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Permissions-Policy",
                        containsString("camera=()")));
    }

    /**
     * HSTS is only emitted on a secure request - a browser must not be told to
     * pin HTTPS by a plaintext response, since that response could itself be
     * forged. MockMvc requests are plaintext unless told otherwise.
     */
    @Test
    @DisplayName("HSTS is sent on HTTPS requests")
    void hstsOnSecureRequests() throws Exception {
        mockMvc.perform(get("/api/properties").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security",
                        containsString("max-age=31536000")))
                .andExpect(header().string("Strict-Transport-Security",
                        containsString("includeSubDomains")));
    }

    @Test
    @DisplayName("public property reads are cacheable for 60 seconds and carry an ETag")
    void publicReadsAreCacheable() throws Exception {
        mockMvc.perform(get("/api/properties"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=60")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("public")))
                .andExpect(header().string(HttpHeaders.ETAG, notNullValue()));
    }

    /**
     * The 304 path. Without it the ETag is decoration: the body is still sent
     * on every revalidation, which on a listing page is most of the bytes.
     */
    @Test
    @DisplayName("a matching If-None-Match gets 304 with no body")
    void repeatReadGets304() throws Exception {
        String etag = mockMvc.perform(get("/api/properties"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        mockMvc.perform(get("/api/properties").header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified());
    }

    /**
     * Drafts and personal data must never sit in a shared cache. Spring
     * Security's default writer supplies no-store; this pins it, because losing
     * it would be invisible until a proxy started serving one admin's view to
     * another.
     */
    @Test
    @DisplayName("admin reads are not cacheable")
    void adminReadsAreNotCacheable() throws Exception {
        mockMvc.perform(get("/api/properties/admin").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    }

    @Test
    @DisplayName("auth responses are not cacheable")
    void authResponsesAreNotCacheable() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@iunu.test\",\"password\":\"WrongPassword1\"}"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    }

    /** The admin listing serves drafts; an ETag there invites a cache to keep them. */
    @Test
    @DisplayName("the admin listing carries no ETag")
    void adminListingHasNoEtag() throws Exception {
        mockMvc.perform(get("/api/properties/admin").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ETAG));
    }

    /**
     * Spring Security's method matcher is exact, so a rule written for GET
     * alone answers 401 to a HEAD of the same URL. Nothing in the app issues a
     * HEAD, which is why this went unnoticed - but CDN cache validation, link
     * previews and uptime checks all do.
     */
    @Test
    @DisplayName("HEAD is allowed wherever GET is public")
    void headIsAllowedOnPublicPaths() throws Exception {
        mockMvc.perform(head("/api/properties")).andExpect(status().isOk());
        mockMvc.perform(head("/api/projects")).andExpect(status().isOk());
        mockMvc.perform(head("/actuator/health")).andExpect(status().isOk());
    }

    /** And HEAD must not become a way around an admin-only rule. */
    @Test
    @DisplayName("HEAD on an admin path is still refused")
    void headDoesNotBypassAdminRules() throws Exception {
        mockMvc.perform(head("/api/properties/admin")).andExpect(status().isUnauthorized());
        mockMvc.perform(head("/actuator/metrics")).andExpect(status().isUnauthorized());
    }
}
