package com.iunu.realestate.security;

import com.iunu.realestate.security.events.SecurityEventType;
import com.iunu.realestate.security.events.SecurityEvents;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final SecurityEvents securityEvents;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            // Parsed here rather than through isValid(), which swallows the
            // reason: an expired token is a client that needs to refresh, a bad
            // signature is someone forging one, and the two read very
            // differently in an incident.
            String email = jwtService.parseAndValidate(token).getSubject();

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                var authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (ExpiredJwtException e) {
            rejectToken(request, "expired");
        } catch (JwtException | IllegalArgumentException e) {
            rejectToken(request, "invalid");
        } catch (org.springframework.security.core.AuthenticationException e) {
            // Well-signed, but for an account that no longer loads - deleted,
            // or disabled since the token was issued.
            rejectToken(request, "unknown_account");
        }

        filterChain.doFilter(request, response);
    }

    /** The token itself is never logged, not even a prefix of it. */
    private void rejectToken(HttpServletRequest request, String reason) {
        SecurityContextHolder.clearContext();
        securityEvents.record(SecurityEventType.TOKEN_INVALID, null, null, request,
                Map.of("reason", reason, "path", request.getRequestURI()));
    }
}
