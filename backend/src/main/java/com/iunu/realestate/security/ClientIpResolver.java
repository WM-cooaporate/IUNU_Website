package com.iunu.realestate.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
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
    private final List<CidrBlock> trustedProxies;

    public ClientIpResolver(
            @Value("${app.client-ip.mode:}") String configuredMode,
            @Value("${app.client-ip.trusted-proxy-hops:1}") int trustedProxyHops,
            // The property this replaces. Honoured so an existing deployment
            // that sets RATE_LIMIT_TRUST_FORWARDED_HEADER=true keeps working
            // across this release without a coordinated env change - if
            // app.client-ip.mode is unset, the old boolean still decides.
            @Value("${app.security.trust-forwarded-header:false}") boolean legacyTrustForwardedHeader,
            @Value("${app.client-ip.trusted-proxies:}") String trustedProxies
    ) {
        this.trustedProxyHops = Math.max(1, trustedProxyHops);
        this.mode = resolveMode(configuredMode, legacyTrustForwardedHeader);
        this.trustedProxies = CidrBlock.parseList(trustedProxies);

        if (this.mode != Mode.REMOTE_ADDR && this.trustedProxies.isEmpty()) {
            log.warn("app.client-ip.mode={} with no app.client-ip.trusted-proxies (TRUSTED_PROXIES) set. "
                            + "Forwarding headers will be honoured from ANY peer, so anything that can reach this "
                            + "process directly - the platform's own *.onrender.com / *.up.railway.app URL, for "
                            + "example - can choose its own rate-limit bucket by setting the header itself. "
                            + "Set TRUSTED_PROXIES to the proxy's address range, or close the origin "
                            + "(EDGE_SHARED_SECRET). See docs/DDOS_RUNBOOK.md.",
                    this.mode);
        }
    }

    /**
     * Whether the immediate peer is a proxy we are willing to believe.
     *
     * <p>This is the check that makes forwarding headers trustworthy at all.
     * {@code X-Forwarded-For} is append-only <em>if a proxy appended to it</em>;
     * a request that reaches this process directly carries whatever the caller
     * typed, end to end, so "the last entry is the real client" holds only for
     * traffic that genuinely came through the hop it claims to have come
     * through. The socket address is the one thing in a request a caller cannot
     * forge, so it is what decides.
     *
     * <p>With no allowlist configured this returns true, preserving the older
     * behaviour - and the constructor warns about exactly what that costs.
     */
    /**
     * Whether forwarding headers from this request's peer may be believed at
     * all. The same rule decides the client address here and the scheme in
     * {@link ForwardedProtoFilter}, so there is one trust decision for every
     * forwarded header, not one per component.
     */
    public boolean isTrustedPeer(HttpServletRequest request) {
        return peerIsTrustedProxy(request);
    }

    private boolean peerIsTrustedProxy(HttpServletRequest request) {
        if (trustedProxies.isEmpty()) {
            return true;
        }
        String peer = request.getRemoteAddr();
        return peer != null && trustedProxies.stream().anyMatch(block -> block.contains(peer));
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
        if (mode == Mode.REMOTE_ADDR || !peerIsTrustedProxy(request)) {
            // Not from a hop we believe: the headers are just text the caller
            // sent. Falling back to the socket address is over-restrictive
            // (several clients behind one NAT share a bucket) rather than
            // unrestricted, which is the right direction to be wrong in.
            return request.getRemoteAddr();
        }
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

    /**
     * One CIDR block, matched by comparing the leading {@code prefixLength}
     * bits of the address.
     *
     * <p>Written out rather than pulled from a library because it is twenty
     * lines and adding a dependency to this codebase for it would be a worse
     * trade. A bare address (no {@code /n}) is a /32 or /128 - an exact match.
     */
    record CidrBlock(byte[] network, int prefixLength) {

        static List<CidrBlock> parseList(String commaSeparated) {
            if (commaSeparated == null || commaSeparated.isBlank()) {
                return List.of();
            }
            List<CidrBlock> blocks = new ArrayList<>();
            for (String entry : commaSeparated.split(",")) {
                String trimmed = entry.trim();
                if (!trimmed.isEmpty()) {
                    blocks.add(parse(trimmed));
                }
            }
            return List.copyOf(blocks);
        }

        static CidrBlock parse(String cidr) {
            String[] parts = cidr.split("/", 2);
            InetAddress address;
            try {
                address = InetAddress.getByName(parts[0]);
            } catch (UnknownHostException exception) {
                // A hostname here would be resolved at startup and could change
                // underneath us; only literal addresses are accepted.
                throw new IllegalStateException(
                        "app.client-ip.trusted-proxies (TRUSTED_PROXIES) entries must be literal IP addresses or "
                                + "CIDR blocks. Got: " + cidr, exception);
            }
            byte[] bytes = address.getAddress();
            int maxPrefix = bytes.length * 8;
            int prefix = parts.length == 2 ? Integer.parseInt(parts[1].trim()) : maxPrefix;
            if (prefix < 0 || prefix > maxPrefix) {
                throw new IllegalStateException(
                        "app.client-ip.trusted-proxies (TRUSTED_PROXIES) prefix length out of range for "
                                + cidr + " (0-" + maxPrefix + ")");
            }
            return new CidrBlock(bytes, prefix);
        }

        boolean contains(String candidate) {
            byte[] bytes;
            try {
                bytes = InetAddress.getByName(candidate).getAddress();
            } catch (UnknownHostException exception) {
                return false;
            }
            // An IPv4 block never contains an IPv6 address, and vice versa.
            if (bytes.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (bytes[i] != network[i]) {
                    return false;
                }
            }
            int remainingBits = prefixLength % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (bytes[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
