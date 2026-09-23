package com.iunu.realestate.security.events;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.iunu.realestate.security.ClientIpResolver;
import com.iunu.realestate.util.LogSanitizer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;

import static net.logstash.logback.argument.StructuredArguments.keyValue;

/**
 * The one place security events are written. Every detection point - login,
 * refresh, the filters, the access-denied handler, upload validation - calls
 * {@link #record}; nothing else writes to the {@code SECURITY} logger.
 *
 * <p>Each call does two things:
 *
 * <ul>
 *   <li><strong>Counts</strong> it on {@code iunu.security.events{type}}. Always,
 *       with no sampling, because the counter is what the alert rules read and
 *       a sampled counter would under-report a flood exactly when it matters.
 *       The only tag is the enum name - the same rule as {@code AbuseMetrics}:
 *       a tag built from anything a caller sends is a memory-exhaustion vector.</li>
 *   <li><strong>Logs</strong> it to the {@code SECURITY} logger, so the log
 *       platform can filter on that name alone.</li>
 * </ul>
 *
 * <p><strong>Noisy types are sampled in the log.</strong> A flood of 429s or
 * probes must not bury the one line that matters, or fill the disk. For those
 * types at most one line per {@code (type, client IP)} per minute is written;
 * the counter still sees every one. The sampling state is a bounded Caffeine
 * cache for the same reason the rate limiter's bucket store is: a map keyed on
 * client address that never evicts is an OOM an attacker can drive.
 *
 * <p><strong>What never reaches a log line:</strong> passwords, tokens of any
 * kind, API keys, the edge secret, request bodies. Emails go through
 * {@link LogSanitizer#maskEmail}. Detail values are stripped of control
 * characters and truncated, so free text cannot forge a log line.
 */
@Component
public class SecurityEvents {

    public static final String LOGGER_NAME = "SECURITY";
    public static final String METRIC = "iunu.security.events";

    private static final Logger SECURITY_LOG = LoggerFactory.getLogger(LOGGER_NAME);

    /** A human should look at these today, so they are WARN; everything else is INFO. */
    private static final Set<SecurityEventType> WARN_TYPES = EnumSet.of(
            SecurityEventType.ACCOUNT_LOCKED,
            SecurityEventType.REFRESH_REUSE_DETECTED,
            SecurityEventType.ADMIN_LOGIN_NEW_IP,
            SecurityEventType.EDGE_SECRET_REJECTED);

    /**
     * Types an unauthenticated caller can generate at will. EDGE_SECRET_REJECTED
     * is here too although it is a WARN: someone who has found the origin can
     * send it a million requests, and a million identical WARN lines hide
     * everything else just as well as a million INFO ones.
     */
    private static final Set<SecurityEventType> SAMPLED_TYPES = EnumSet.of(
            SecurityEventType.RATE_LIMITED,
            SecurityEventType.ACCESS_DENIED,
            SecurityEventType.TOKEN_INVALID,
            SecurityEventType.LOGIN_FAILED,
            SecurityEventType.EDGE_SECRET_REJECTED);

    private static final int MAX_DETAIL_VALUE_LENGTH = 200;
    private static final int MAX_IP_LENGTH = 64;

    private final Map<SecurityEventType, Counter> counters = new EnumMap<>(SecurityEventType.class);
    private final Cache<String, Boolean> recentlyLogged;
    private final ClientIpResolver clientIpResolver;

    public SecurityEvents(
            MeterRegistry registry,
            ClientIpResolver clientIpResolver,
            // Injectable so a test can prove the bound without ten thousand calls.
            @Value("${app.security.events.max-sampled-clients:10000}") long maxSampledClients
    ) {
        this.clientIpResolver = clientIpResolver;
        this.recentlyLogged = Caffeine.newBuilder()
                .maximumSize(maxSampledClients)
                .expireAfterWrite(Duration.ofMinutes(1))
                .build();

        // Registered up front, so a flat line on a dashboard reads as "none"
        // rather than "this metric does not exist yet".
        for (SecurityEventType type : SecurityEventType.values()) {
            counters.put(type, Counter.builder(METRIC)
                    .description("Security-relevant events, by type")
                    .tag("type", type.name())
                    .register(registry));
        }
    }

    /**
     * Records one event.
     *
     * @param userId   the account involved, when one is known
     * @param email    the account's email, when one is known; masked before logging
     * @param clientIp the resolved client address (see {@link ClientIpResolver})
     * @param detail   small, fixed-meaning facts about the event. Keys are
     *                 chosen by code; values are sanitised and truncated.
     *                 Never a token, a password or a request body.
     */
    public void record(SecurityEventType type, @Nullable Long userId, @Nullable String email,
                       @Nullable String clientIp, @Nullable Map<String, String> detail) {
        counters.get(type).increment();

        String ip = clean(clientIp, MAX_IP_LENGTH);
        if (SAMPLED_TYPES.contains(type) && !firstThisMinute(type, ip)) {
            return;
        }

        Object[] arguments = {
                keyValue("event", type.name()),
                keyValue("userId", userId),
                keyValue("email", LogSanitizer.maskEmail(email)),
                keyValue("clientIp", ip),
                keyValue("detail", renderDetail(detail))
        };
        String format = "security_event {} {} {} {} {}";
        if (WARN_TYPES.contains(type)) {
            SECURITY_LOG.warn(format, arguments);
        } else {
            SECURITY_LOG.info(format, arguments);
        }
    }

    /** Same as {@link #record(SecurityEventType, Long, String, String, Map)}, resolving the IP from {@code request}. */
    public void record(SecurityEventType type, @Nullable Long userId, @Nullable String email,
                       HttpServletRequest request, @Nullable Map<String, String> detail) {
        record(type, userId, email, clientIpResolver.resolve(request), detail);
    }

    /**
     * Same again, for callers deep in a service with no request in hand. Uses
     * the request bound to this thread, or "-" outside one (a scheduled task).
     */
    public void record(SecurityEventType type, @Nullable Long userId, @Nullable String email,
                       @Nullable Map<String, String> detail) {
        record(type, userId, email, currentClientIp(), detail);
    }

    /** The resolved client address of the request on this thread, or "-" when there is none. */
    public String currentClientIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            return clientIpResolver.resolve(servlet.getRequest());
        }
        return "-";
    }

    /** Package-private so a test can see the bound hold. */
    long sampledClientCount() {
        recentlyLogged.cleanUp();
        return recentlyLogged.estimatedSize();
    }

    private boolean firstThisMinute(SecurityEventType type, String ip) {
        return recentlyLogged.asMap().putIfAbsent(type.name() + '|' + ip, Boolean.TRUE) == null;
    }

    /** Sorted, so the same event always renders the same way and a log search can match it. */
    private static String renderDetail(@Nullable Map<String, String> detail) {
        if (detail == null || detail.isEmpty()) {
            return "{}";
        }
        StringJoiner joiner = new StringJoiner(", ", "{", "}");
        new TreeMap<>(detail).forEach((key, value) ->
                joiner.add(clean(key, 40) + "=" + clean(value, MAX_DETAIL_VALUE_LENGTH)));
        return joiner.toString();
    }

    /**
     * Control characters - not only CR and LF but every C0/C1 character and
     * the Unicode line and paragraph separators - become a space, so a value
     * cannot end the line it is on and write a convincing one of its own.
     */
    static String clean(@Nullable String value, int maxLength) {
        if (value == null) {
            return "-";
        }
        String flattened = value.replaceAll("[\\p{Cntrl}\\u0085\\u2028\\u2029]", " ");
        return flattened.length() > maxLength ? flattened.substring(0, maxLength) + "..." : flattened;
    }
}
