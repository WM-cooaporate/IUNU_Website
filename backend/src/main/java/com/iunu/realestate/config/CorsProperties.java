package com.iunu.realestate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Bound from app.cors.allowed-origins (env: CORS_ALLOWED_ORIGINS, comma-separated). */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
