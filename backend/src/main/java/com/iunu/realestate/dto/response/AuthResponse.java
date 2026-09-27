package com.iunu.realestate.dto.response;

import java.time.Instant;

/**
 * Login / refresh result.
 *
 * {@code expiresAt} is the absolute ISO-8601 expiry of {@code accessToken},
 * which is what a client actually needs to decide when to refresh;
 * {@code expiresInSeconds} is kept alongside it for existing callers.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        Instant expiresAt,
        UserResponse user
) {
    public static AuthResponse of(String accessToken, String refreshToken, long expiresInSeconds, UserResponse user) {
        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                expiresInSeconds,
                Instant.now().plusSeconds(expiresInSeconds),
                user
        );
    }
}
