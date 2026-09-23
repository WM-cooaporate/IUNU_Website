package com.iunu.realestate.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gives every request an id, puts it on every log line the request produces
 * (MDC {@code requestId}), stores it on audit rows, and echoes it back in
 * {@code X-Request-Id} - so "the dashboard showed an error at 14:02" can be
 * turned into the exact log lines for that request.
 *
 * <p>First in the chain, ahead of the edge-secret check and Spring Security,
 * so even a request those refuse is traceable.
 *
 * <p>An incoming {@code X-Request-Id} is honoured only if it is a well-formed
 * UUID. Anything else is replaced: the value lands in every log line and in
 * the audit table, and a caller-chosen free-text id is a log-injection and
 * log-search-poisoning primitive.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Pattern UUID_SHAPE =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = accept(request.getHeader(HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Tomcat reuses threads. A leftover id would be stamped on the next
            // request's log lines, which is worse than having none.
            MDC.remove(MDC_KEY);
        }
    }

    static String accept(String candidate) {
        if (candidate != null && UUID_SHAPE.matcher(candidate).matches()) {
            return candidate.toLowerCase();
        }
        return UUID.randomUUID().toString();
    }
}
