package com.iunu.realestate.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iunu.realestate.dto.response.ApiError;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coarse, in-memory per-IP rate limiting for the public, unauthenticated
 * endpoints most likely to be abused: login/register/forgot-password
 * (credential stuffing / enumeration) and the public lead-generation forms
 * (spam). This is intentionally simple (no Redis) since the app is meant
 * to run as a single instance; swap for a distributed limiter if scaled out.
 */
@Component
@Order(0)
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Value("${app.security.trust-forwarded-header:false}")
    private boolean trustForwardedHeader;

    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> writeBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();
        Bucket bucket = null;

        if ("POST".equals(method) && (path.equals("/api/auth/login") || path.equals("/api/auth/register")
                || path.equals("/api/auth/forgot-password") || path.equals("/api/auth/reset-password"))) {
            bucket = loginBuckets.computeIfAbsent(clientKey(request), k -> newLoginBucket());
        } else if ("POST".equals(method) && (path.equals("/api/contact") || path.equals("/api/quotes")
                || path.equals("/api/newsletter"))) {
            bucket = writeBuckets.computeIfAbsent(clientKey(request), k -> newWriteBucket());
        }

        if (bucket != null && !bucket.tryConsume(1)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiError body = ApiError.of(
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "Too Many Requests",
                    "Too many requests. Please try again later.",
                    path
            );
            objectMapper.writeValue(response.getWriter(), body);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Bucket newLoginBucket() {
        // 10 attempts per minute, refilled gradually, per client IP.
        Bandwidth limit = Bandwidth.builder().capacity(10).refillIntervally(10, Duration.ofMinutes(1)).build();
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket newWriteBucket() {
        // 20 submissions per hour per client IP for the public lead forms.
        Bandwidth limit = Bandwidth.builder().capacity(20).refillIntervally(20, Duration.ofHours(1)).build();
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * X-Forwarded-For is only trusted when the app is explicitly configured to
     * sit behind a reverse proxy (app.security.trust-forwarded-header=true) -
     * otherwise a direct client could spoof it to bypass the per-IP limit.
     */
    private String clientKey(HttpServletRequest request) {
        if (trustForwardedHeader) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
