package com.iunu.realestate.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iunu.realestate.dto.response.ApiError;
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
import java.util.Locale;

/**
 * Rejects an oversized non-multipart body before anything reads it.
 *
 * <p>The {@code @Size} constraints on the request DTOs are not a defence here:
 * bean validation runs after Jackson has parsed the entire body into objects.
 * A 50MB JSON string is fully read, decoded and allocated before the first
 * constraint is checked. This filter looks only at the declared
 * {@code Content-Length} and answers 413 without touching the stream.
 *
 * <p>Multipart is skipped deliberately - uploads are governed by
 * {@code spring.servlet.multipart.max-file-size} / {@code max-request-size},
 * which enforce a per-file limit this filter cannot express, and whose
 * {@code MaxUploadSizeExceededException} the global handler already maps to
 * 413.
 *
 * <p><strong>Limit:</strong> a chunked request sends no Content-Length, so this
 * check cannot see its size. Tomcat's {@code max-swallow-size} and
 * {@code max-http-form-post-size} bound the common cases, and Jackson 2.15+
 * {@code StreamReadConstraints} (nesting depth, string and number length) bound
 * what a parsed document can cost. Fully closing that gap needs a body-size cap
 * at the edge, which is one of the Cloudflare steps in docs/DDOS_RUNBOOK.md.
 */
// Before Spring Security's chain (Boot registers that at order -100), so an
// oversized body is refused without being buffered or parsed by anything.
@Component
@Order(-110)
@RequiredArgsConstructor
public class ContentLengthLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    /**
     * 1MB. The largest legitimate JSON this API takes is a property with a long
     * description and a gallery of URLs - a few kilobytes. A megabyte is three
     * orders of magnitude of headroom.
     */
    @Value("${app.security.max-json-request-bytes:1048576}")
    private long maxJsonRequestBytes;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        if (isOversized(request)) {
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiError body = ApiError.of(
                    HttpStatus.PAYLOAD_TOO_LARGE.value(),
                    "Payload Too Large",
                    "The request body is too large",
                    request.getRequestURI());
            objectMapper.writeValue(response.getWriter(), body);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isOversized(HttpServletRequest request) {
        // getContentLengthLong() is -1 for chunked encoding and for requests
        // with no body. Neither is something this filter can judge.
        long declaredLength = request.getContentLengthLong();
        if (declaredLength < 0 || declaredLength <= maxJsonRequestBytes) {
            return false;
        }
        return !isMultipart(request);
    }

    private static boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null
                && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }
}
