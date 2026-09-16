package com.iunu.realestate.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Works out which address a per-IP limit should be keyed on. Getting this
 * wrong breaks the rate limiter in one of two ways, and both are silent:
 *
 * <ul>
 *   <li><strong>Too little trust.</strong> Behind Render, Railway or
 *       Cloudflare, {@code getRemoteAddr()} is the <em>proxy's</em> address,
 *       identical for everyone. Every visitor then shares one bucket: an
 *       attacker who spends the login allowance locks every real admin out of
 *       the dashboard, and the limiter becomes a denial-of-service tool
 *       pointed at the client.</li>
 *   <li><strong>Too much trust.</strong> Take the leftmost
 *       {@code X-Forwarded-For} entry and the key is whatever the caller
 *       typed. An attacker sends a different fake IP per request, lands in a
 *       fresh bucket every time, and the limit may as well not exist - while
 *       also growing the bucket store without bound.</li>
 * </ul>
 *
 * <p>Which strategy is correct depends on the deployment, not on the code, so
 * it is configuration: {@code app.client-ip.mode}.
 */
@Slf4j
@Component
public class ClientIpResolver {

    /** Cloudflare's header. Set by Cloudflare itself; cannot be forged through it. */
    private static final String CF_CONNECTING_IP = "CF-Connecting-IP";

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    public enum Mode {
        /**
         * {@code getRemoteAddr()} verbatim. Correct for local development and
         * for any deployment with no proxy in front. Wrong - and dangerously
         * so - behind one, where it collapses every client into one bucket.
         */
        REMOTE_ADDR,

        /**
         * The entry {@code trusted-proxy-hops} positions from the <em>right</em>
         * of {@code X-Forwarded-For}. For Render or Railway with no Cloudflare.
         * See {@link #fromForwardedFor} for why the right, never the left.
         */
        X_FORWARDED_FOR_MODE,

        /**
         * {@code CF-Connecting-IP}. Only safe when the origin accepts traffic
         * from Cloudflare and nowhere else - otherwise anyone who knows the
         * origin URL sends the header themselves and picks their own bucket.
         * The platform URL (*.onrender.com, *.up.railway.app) stays reachable
         * by default, so this mode needs the edge shared secret
         * ({@code EDGE_SHARED_SECRET}, see EdgeSecretFilter) or an equivalent
         * origin lock to actually hold. Documented in docs/DDOS_RUNBOOK.md.
         */
        CLOUDFLARE
    }

    private final Mode mode;
    private final int trustedProxyHops;

    public ClientIpResolver(
            @Value("${app.client-ip.mode:}") String configuredMode,
            @Value("${app.client-ip.trusted-proxy-hops:1}") int trustedProxyHops,
            // The property this replaces. Honoured so an existing deployment
            // that sets RATE_LIMIT_TRUST_FORWARDED_HEADER=true keeps working
            // across this release without a coordinated env change - if
            // app.client-ip.mode is unset, the old boolean still decides.
            @Value("${app.security.trust-forwarded-header:false}") boolean legacyTrustForwardedHeader
    ) {
        this.trustedProxyHops = Math.max(1, trustedProxyHops);
        this.mode = resolveMode(configuredMode, legacyTrustForwardedHeader);
    }

    private static Mode resolveMode(String configuredMode, boolean legacyTrustForwardedHeader) {
        if (configuredMode != null && !configuredMode.isBlank()) {
            return switch (configuredMode.trim().toLowerCase(Locale.ROOT)) {
                case "remote-addr" -> Mode.REMOTE_ADDR;
                case "x-forwarded-for" -> Mode.X_FORWARDED_FOR_MODE;
                case "cloudflare" -> Mode.CLOUDFLARE;
                default -> throw new IllegalStateException(
                        "app.client-ip.mode (CLIENT_IP_MODE) must be one of: remote-addr, x-forwarded-for, "
                                + "cloudflare. Got: " + configuredMode);
            };
        }
        return legacyTrustForwardedHeader ? Mode.X_FORWARDED_FOR_MODE : Mode.REMOTE_ADDR;
    }

    public Mode mode() {
        return mode;
    }

    /**
     * The address to key per-IP limits on. Never null: every branch falls back
     * to {@code getRemoteAddr()}, so a missing or malformed header degrades to
     * over-restrictive (one shared bucket) rather than to no limit at all.
     */
    public String resolve(HttpServletRequest request) {
        return switch (mode) {
            case REMOTE_ADDR -> request.getRemoteAddr();
            case CLOUDFLARE -> firstNonBlank(request.getHeader(CF_CONNECTING_IP), request.getRemoteAddr());
            case X_FORWARDED_FOR_MODE -> fromForwardedFor(request);
        };
    }

    /**
     * Picks the entry {@code trustedProxyHops} from the right.
     *
     * <p>{@code X-Forwarded-For} is append-only: each proxy adds the address it
     * received the request from. A client may send the header itself, and the
     * proxy will happily append to whatever it was given - so the <em>left</em>
     * of the list is entirely attacker-controlled and the <em>right</em> is
     * what proxies wrote.
     *
     * <p>With one trusted hop the last entry is the address that hop saw, which
     * is the real client. With two (Cloudflare in front of Render, say) the
     * last entry is Cloudflare's own address and the real client is one
     * further left - hence the configurable count. Set it to the number of
     * proxies that actually sit in front of this app; too high and you start
     * reading entries a client could have forged.
     */
    private String fromForwardedFor(HttpServletRequest request) {
        String header = request.getHeader(X_FORWARDED_FOR);
        if (header == null || header.isBlank()) {
            return request.getRemoteAddr();
        }

        String[] hops = header.split(",");
        int index = hops.length - trustedProxyHops;
        if (index < 0) {
            // Fewer entries than configured hops: the header is shorter than
            // the deployment says it should be, so nothing in it is trustworthy.
            // The leftmost entry is the closest to "the client", but it is also
            // the one a caller can set, so fall back to the socket address.
            return request.getRemoteAddr();
        }
        return firstNonBlank(hops[index].trim(), request.getRemoteAddr());
    }

    private static String firstNonBlank(String candidate, String fallback) {
        return (candidate == null || candidate.isBlank()) ? fallback : candidate.trim();
    }
}
