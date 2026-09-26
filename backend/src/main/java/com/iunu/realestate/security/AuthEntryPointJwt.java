package com.iunu.realestate.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iunu.realestate.dto.response.ApiError;
import com.iunu.realestate.security.events.SecurityEventType;
import com.iunu.realestate.security.events.SecurityEvents;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.nio.charset.StandardCharsets;

/** Handles requests to protected endpoints with no/invalid credentials -> 401. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthEntryPointJwt implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final SecurityEvents securityEvents;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        log.debug("Unauthorized request to {}: {}", request.getRequestURI(), authException.getMessage());
        // Recorded as ACCESS_DENIED (status 401) so an anonymous scan of the
        // admin API shows up in the same Probing alert as a signed-in one.
        // Without it a scanner that never sends a token raises nothing at all.
        securityEvents.record(SecurityEventType.ACCESS_DENIED, null, null, request,
                Map.of("status", "401", "method", request.getMethod(), "path", request.getRequestURI()));

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        // Explicit UTF-8: without it the servlet default is ISO-8859-1, which
        // mangles any non-ASCII text in the message and contradicts JSON.
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");

        ApiError body = ApiError.of(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "Authentication is required to access this resource",
                request.getRequestURI()
        );

        objectMapper.writeValue(response.getWriter(), body);
    }
}
