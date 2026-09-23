package com.iunu.realestate.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Tells the app a request arrived over HTTPS when the proxy in front says so,
 * and does nothing else.
 *
 * <p>TLS ends at the platform's proxy, so without this every request looks
 * like plain HTTP - and Spring Security only sends Strict-Transport-Security
 * on a secure request, so HSTS would silently stop.
 *
 * <p>It replaces {@code server.forward-headers-strategy}, which cannot be used
 * here (N6 in docs/SECURITY_AUDIT.md). Both of its modes also rewrite the
 * client address from X-Forwarded-For: {@code framework} takes the leftmost -
 * caller-typed - entry, and {@code native} does the same when every hop is
 * trusted. Either one hands an attacker their choice of rate-limit bucket.
 * The client address is ClientIpResolver's job alone; this filter reads only
 * X-Forwarded-Proto, and only from a peer ClientIpResolver trusts.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ForwardedProtoFilter extends OncePerRequestFilter {

    private static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";

    private final ClientIpResolver clientIpResolver;

    public ForwardedProtoFilter(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String proto = request.getHeader(X_FORWARDED_PROTO);
        if (proto != null && "https".equalsIgnoreCase(proto.split(",")[0].trim())
                && !request.isSecure() && clientIpResolver.isTrustedPeer(request)) {
            filterChain.doFilter(new HttpsRequest(request), response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static final class HttpsRequest extends HttpServletRequestWrapper {
        HttpsRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public boolean isSecure() {
            return true;
        }

        @Override
        public String getScheme() {
            return "https";
        }
    }
}
