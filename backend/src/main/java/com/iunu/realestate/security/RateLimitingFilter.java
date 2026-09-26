package com.iunu.realestate.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.iunu.realestate.dto.response.ApiError;
import com.iunu.realestate.metrics.AbuseMetrics;
import com.iunu.realestate.security.events.SecurityEventType;
import com.iunu.realestate.security.events.SecurityEvents;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;
import java.nio.charset.StandardCharsets;

/**
 * Per-client rate limiting for everything an attacker would point a script at.
 *
 * <p>Four buckets, each answering a different abuse:
 *
 * <table>
 *   <tr><td>login</td>      <td>10/min per IP</td>  <td>credential stuffing, account enumeration, and BCrypt(12) as a CPU sink</td></tr>
 *   <tr><td>write</td>      <td>20/hr per IP</td>   <td>spam through the public lead forms</td></tr>
 *   <tr><td>public</td>     <td>300/min per IP</td> <td>scripted scraping loops; generous enough that real browsing never sees it</td></tr>
 *   <tr><td>admin</td>      <td>120/min per user</td><td>a stolen admin token looping an expensive endpoint</td></tr>
 *   <tr><td>translation</td><td>30/min per user</td> <td>the paid Google API, billed per character</td></tr>
 * </table>
 *
 * <p>Two things here are load-bearing and easy to get wrong:
 *
 * <p><strong>The bucket store is bounded.</strong> It used to be a plain
 * {@code ConcurrentHashMap}, which never evicts. Requests from many addresses -
 * a botnet, or forged {@code X-Forwarded-For} values under a misconfiguration -
 * grow it until the JVM dies. A limiter that can be turned into an OOM is
 * worse than none. Caffeine caps it and expires idle entries.
 *
 * <p><strong>The admin bucket keys on the user, not the IP.</strong> Several
 * admins behind one office NAT must not share an allowance. The user id is read
 * straight from the JWT signature rather than from the SecurityContext, because
 * this filter runs <em>before</em> authentication on purpose - so that an
 * unauthenticated flood is rejected before it reaches the database lookup in
 * {@link JwtAuthenticationFilter}. Verifying an HMAC costs microseconds and no
 * I/O; it is the cheapest identity available at this point in the chain.
 *
 * <p>Still per-JVM, like the cache. A second instance multiplies every limit by
 * the instance count - move this to Redis before scaling out.
 */
@Component
@Order(0)
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;
    private final JwtService jwtService;
    private final AbuseMetrics metrics;
    private final SecurityEvents securityEvents;
    private final boolean enabled;

    private final Cache<String, Bucket> loginBuckets;
    private final Cache<String, Bucket> writeBuckets;
    private final Cache<String, Bucket> publicBuckets;
    private final Cache<String, Bucket> adminBuckets;
    private final Cache<String, Bucket> translationBuckets;

    public RateLimitingFilter(
            ObjectMapper objectMapper,
            ClientIpResolver clientIpResolver,
            JwtService jwtService,
            AbuseMetrics metrics,
            SecurityEvents securityEvents,
            // Off under the test profile: the integration suite shares one
            // application context, so a limiter counting across every test in
            // the run makes failures depend on test order. One dedicated class
            // turns it back on and exercises it in isolation.
            @Value("${app.security.rate-limit.enabled:true}") boolean enabled,
            // Injectable so a test can prove eviction happens without sending
            // a hundred thousand requests.
            @Value("${app.security.rate-limit.max-tracked-clients:100000}") long maxTrackedClients
    ) {
        this.objectMapper = objectMapper;
        this.clientIpResolver = clientIpResolver;
        this.jwtService = jwtService;
        this.metrics = metrics;
        this.securityEvents = securityEvents;
        this.enabled = enabled;

        this.loginBuckets = boundedStore(maxTrackedClients);
        this.writeBuckets = boundedStore(maxTrackedClients);
        this.publicBuckets = boundedStore(maxTrackedClients);
        this.adminBuckets = boundedStore(maxTrackedClients);
        this.translationBuckets = boundedStore(maxTrackedClients);
    }

    /**
     * expireAfterAccess rather than expireAfterWrite: an entry is only worth
     * keeping while its client is still sending requests. Evicting a bucket
     * early hands that client a fresh allowance, which is why the cap is high
     * enough that only an attack reaches it - under normal traffic nothing is
     * ever evicted for size.
     */
    private static Cache<String, Bucket> boundedStore(long maximumSize) {
        return Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterAccess(Duration.ofHours(1))
                .build();
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        Limit limit = limitFor(request);
        if (limit == null) {
            filterChain.doFilter(request, response);
            return;
        }

        ConsumptionProbe probe = limit.bucket().tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        metrics.rateLimitRejected(limit.bucketName());
        // The path is detail, never a key: it is whatever the caller typed.
        securityEvents.record(SecurityEventType.RATE_LIMITED, null, null, ip(request),
                Map.of("bucket", limit.bucketName().tag(), "method", request.getMethod(),
                        "path", request.getRequestURI()));
        reject(request, response, probe.getNanosToWaitForRefill());
    }

    /** The bucket this request draws from, or null when it is not limited. */
    private Limit limitFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("POST".equals(method) && isAuthEndpoint(path)) {
            return limit(AbuseMetrics.Bucket.LOGIN, loginBuckets, ip(request), RateLimitingFilter::newLoginBucket);
        }
        if ("POST".equals(method) && isPublicForm(path)) {
            return limit(AbuseMetrics.Bucket.WRITE, writeBuckets, ip(request), RateLimitingFilter::newWriteBucket);
        }
        if ("POST".equals(method) && path.equals("/api/admin/translations/preview")) {
            return limit(AbuseMetrics.Bucket.TRANSLATION, translationBuckets,
                    principalKey(request), RateLimitingFilter::newTranslationBucket);
        }
        if (isAdminPath(path) || isMutation(method)) {
            return limit(AbuseMetrics.Bucket.ADMIN, adminBuckets,
                    principalKey(request), RateLimitingFilter::newAdminBucket);
        }
        if ("GET".equals(method) && path.startsWith("/api/")) {
            return limit(AbuseMetrics.Bucket.PUBLIC, publicBuckets, ip(request), RateLimitingFilter::newPublicBucket);
        }
        return null;
    }

    private static boolean isAuthEndpoint(String path) {
        return path.equals("/api/auth/login")
                || path.equals("/api/auth/register")
                || path.equals("/api/auth/forgot-password")
                || path.equals("/api/auth/reset-password");
    }

    private static boolean isPublicForm(String path) {
        return path.equals("/api/contact")
                || path.equals("/api/quotes")
                || path.equals("/api/newsletter")
                || path.equals("/api/careers");
    }

    private static boolean isAdminPath(String path) {
        return path.startsWith("/api/admin/") || path.startsWith("/api/properties/admin");
    }

    private static boolean isMutation(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private Limit limit(AbuseMetrics.Bucket name, Cache<String, Bucket> store, String key, Supplier<Bucket> factory) {
        return new Limit(name, store.get(key, ignored -> factory.get()));
    }

    private String ip(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }

    /**
     * The authenticated user id, or the client IP when there is no usable
     * token. Prefixed so a user id can never collide with an address.
     *
     * <p>Falling back to the IP matters: without it, every anonymous request to
     * an admin path would share one "anonymous" bucket, and an attacker with no
     * token at all could spend it and lock the real admins out. That is the
     * same mistake as keying on the proxy's address.
     */
    private String principalKey(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                Claims claims = jwtService.parseAndValidate(header.substring(BEARER_PREFIX.length()));
                Object userId = claims.get("uid");
                if (userId != null) {
                    return "user:" + userId;
                }
            } catch (Exception ignored) {
                // Unsigned, expired or malformed: not an identity. The request
                // is going to be rejected by the auth filter anyway; here it
                // just counts against the sender's address.
            }
        }
        return "ip:" + ip(request);
    }

    private static Bucket newLoginBucket() {
        return of(Bandwidth.builder().capacity(10).refillIntervally(10, Duration.ofMinutes(1)).build());
    }

    private static Bucket newWriteBucket() {
        return of(Bandwidth.builder().capacity(20).refillIntervally(20, Duration.ofHours(1)).build());
    }

    private static Bucket newAdminBucket() {
        return of(Bandwidth.builder().capacity(120).refillIntervally(120, Duration.ofMinutes(1)).build());
    }

    /** 30/min: enough to translate a form repeatedly while writing it, not enough to loop. */
    private static Bucket newTranslationBucket() {
        return of(Bandwidth.builder().capacity(30).refillIntervally(30, Duration.ofMinutes(1)).build());
    }

    /**
     * 300/min with a greedy refill, so a page that fires a dozen requests at
     * once is not punished for the burst while a sustained scrape still stops
     * at 5/second. A visitor loading every page of the site cannot reach this.
     */
    private static Bucket newPublicBucket() {
        return of(Bandwidth.builder().capacity(300).refillGreedy(300, Duration.ofMinutes(1)).build());
    }

    private static Bucket of(Bandwidth bandwidth) {
        return Bucket.builder().addLimit(bandwidth).build();
    }

    /**
     * 429 with Retry-After, in the same ApiError shape as every other error so
     * the frontend's toUserMessage() reads it without a special case.
     * Retry-After is in seconds and must be at least 1 - a client told to
     * retry in 0 seconds retries immediately and makes the flood worse.
     */
    private void reject(HttpServletRequest request, HttpServletResponse response, long nanosToWait)
            throws IOException {
        long secondsToWait = Math.max(1, Duration.ofNanos(nanosToWait).toSeconds());

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        // Explicit UTF-8: without it the servlet default is ISO-8859-1, which
        // mangles any non-ASCII text in the message and contradicts JSON.
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(secondsToWait));

        ApiError body = ApiError.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too Many Requests",
                "Too many requests. Please try again later.",
                request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }

    private record Limit(AbuseMetrics.Bucket bucketName, Bucket bucket) {
    }
}
