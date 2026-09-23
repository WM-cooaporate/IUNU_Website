package com.iunu.realestate.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "IUNU Real Estate API",
                version = "v1",
                description = "Backend API for the IUNU real estate website (auth, properties, leads)."
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {

    /**
     * The server URL in /v3/api-docs, fixed to the configured public origin.
     * Left to springdoc it is built from the incoming request, and behind
     * Render (TLS ends at the proxy, forward-headers-strategy none) that
     * would be an http:// URL for an https:// API.
     */
    @Bean
    public OpenAPI publicServerUrl(@Value("${app.file-storage.public-base-url:http://localhost:8080}") String publicApiUrl) {
        return new OpenAPI().addServersItem(new Server().url(publicApiUrl.replaceAll("/$", "")));
    }
}
