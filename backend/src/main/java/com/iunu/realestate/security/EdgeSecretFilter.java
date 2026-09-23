package com.iunu.realestate.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iunu.realestate.dto.response.ApiError;
import com.iunu.realestate.security.events.SecurityEventType;
import com.iunu.realestate.security.events.SecurityEvents;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Closes the origin to anything that did not come through the edge.
 *
 * <p>Putting Cloudflare in front of a custom domain does not hide the origin:
 * the platform URL ({@code *.onrender.com}, {@code *.up.railway.app}) keeps
 * serving, and an attacker who finds it - it is in DNS history, in old commits,
 * in certificate transparency logs - sends traffic straight past every WAF
 * rule, rate limit and cache rule configured at the edge. Every edge protection
 * is only as good as the origin being unreachable without it.
 *
 * <p>The fix is a shared secret only Cloudflare knows: a Transform Rule injects
 * a header on every request it forwards, and this filter refuses anything
 * arriving without it. Requests to the platform URL have no such header and get
 * a 403.
 *
 * <p><strong>Disabled unless {@code EDGE_SHARED_SECRET} is set</strong>, because
 * enabling it before the Transform Rule exists takes the whole API offline. The
 * order in docs/DDOS_RUNBOOK.md is: add the rule at the edge, verify traffic
 * still works, then set the variable.
 *
 * <p>{@code /actuator/health} and its liveness/readiness probes are exempt. The hosting platform probes it from
 * inside its own network, never through Cloudflare, and gating it would make
 * every deploy fail its health check and roll back.
 */
// Before Spring Security's own chain, which Boot registers at order -100. A
// request that should never have reached this origin must not get as far as
// authentication, rate limiting or a database lookup.
@Slf4j
@Component
@Order(-120)
public class EdgeSecretFilter extends OncePerRequestFilter {

    /**
     * Named for what it is rather than something guessable like "X-Secret", so
     * it does not stand out in a header dump as the thing to brute force.
     */
    public static final String HEADER = "X-Edge-Auth";

    /**
     * The same three probe paths SecurityConfig leaves anonymous. A platform
     * or uptime monitor pointed at liveness or readiness must keep working
     * with the secret on; exempting only the aggregate path used to turn
     * those into 403s. Exact matches only - nothing else under /actuator.
     */
    private static final java.util.Set<String> HEALTH_PROBES = java.util.Set.of(
            "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness");

    private final ObjectMapper objectMapper;
    private final SecurityEvents securityEvents;
    private final byte[] expectedSecret;

    public EdgeSecretFilter(
            ObjectMapper objectMapper,
            SecurityEvents securityEvents,
            @Value("${app.edge.shared-secret:}") String sharedSecret
    ) {
        this.objectMapper = objectMapper;
        this.securityEvents = securityEvents;
        this.expectedSecret = (sharedSecret == null || sharedSecret.isBlank())
                ? null
                : sharedSecret.getBytes(StandardCharsets.UTF_8);

        if (this.expectedSecret != null) {
            log.info("Edge shared-secret enforcement is ON: requests without the {} header will be refused. "
                    + "The matching Cloudflare Transform Rule must be in place.", HEADER);
        }
    }

    /**
     * Skipping the filter entirely when unconfigured keeps the disabled path
     * free of any per-request work at all.
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return expectedSecret == null || HEALTH_PROBES.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        if (matches(request.getHeader(HEADER))) {
            filterChain.doFilter(request, response);
            return;
        }

        // Someone reached the origin without passing through the edge. The
        // header's value is never logged - only whether one was sent at all.
        securityEvents.record(SecurityEventType.EDGE_SECRET_REJECTED, null, null, request,
                Map.of("headerPresent", String.valueOf(request.getHeader(HEADER) != null),
                        "path", request.getRequestURI()));

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Deliberately says nothing about a header or an edge: a probe of the
        // origin should learn no more than "no".
        ApiError body = ApiError.of(
                HttpStatus.FORBIDDEN.value(), "Forbidden", "Forbidden", request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }

    /**
     * Constant-time compare. A byte-by-byte comparison that returns on the
     * first mismatch leaks the secret one character at a time to anyone who can
     * measure response latency precisely enough.
     */
    private boolean matches(String presented) {
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), expectedSecret);
    }
}
