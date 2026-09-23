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
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/** Handles requests from an authenticated user lacking the required role -> 403. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessDeniedHandlerImpl implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final SecurityEvents securityEvents;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        log.debug("Access denied for {}: {}", request.getRequestURI(), accessDeniedException.getMessage());
        Long userId = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            userId = principal.getId();
        }
        // A signed-in non-admin knocking on an admin path. Sampled per IP in
        // the log, counted in full.
        securityEvents.record(SecurityEventType.ACCESS_DENIED, userId, null, request,
                Map.of("status", "403", "method", request.getMethod(), "path", request.getRequestURI()));

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiError body = ApiError.of(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                "You do not have permission to access this resource",
                request.getRequestURI()
        );

        objectMapper.writeValue(response.getWriter(), body);
    }
}
