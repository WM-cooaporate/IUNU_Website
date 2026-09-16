package com.iunu.realestate.config;

import com.iunu.realestate.security.AccessDeniedHandlerImpl;
import com.iunu.realestate.security.AuthEntryPointJwt;
import com.iunu.realestate.security.JwtAuthenticationFilter;
import com.iunu.realestate.security.RateLimitingFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final AuthEntryPointJwt authEntryPointJwt;
    private final AccessDeniedHandlerImpl accessDeniedHandler;
    private final UserDetailsService userDetailsService;
    private final CorsProperties corsProperties;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        // Do not reveal whether an account exists via timing/error differences.
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // stateless, token-based API - no cookies/session to protect
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(authEntryPointJwt)
                        .accessDeniedHandler(accessDeniedHandler))
                .headers(headers -> headers
                        // This CSP is for a JSON API, not a page: nothing here
                        // is ever rendered, so everything is denied. The
                        // browser-facing policy lives in vercel.json.
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        // Stricter than the usual strict-origin-when-cross-origin.
                        // That policy exists to keep analytics working across
                        // navigations; an API has no navigations to preserve, so
                        // there is no reason to leak the origin at all.
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        // Last in the chain on purpose: in Spring Security 6.3
                        // permissionsPolicy() returns its own config object
                        // rather than the HeadersConfigurer, so nothing can be
                        // chained after it.
                        .permissionsPolicy(permissions -> permissions
                                .policy("camera=(), microphone=(), geolocation=()")))
                .authorizeHttpRequests(auth -> auth
                        // These two live under /api/auth/** but require a valid token -
                        // listed before the blanket permitAll below so they win.
                        .requestMatchers("/api/auth/me", "/api/auth/change-password").authenticated()
                        // Public authentication endpoints
                        .requestMatchers("/api/auth/**").permitAll()
                        // Admin reads of properties live under /api/properties/admin/** and
                        // expose unpublished rows, so they must be matched BEFORE the public
                        // GET rule below - otherwise "/api/properties/**" would permitAll them
                        // and only the controller's @PreAuthorize would stand between a
                        // stranger and every draft.
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/properties/admin", "/api/properties/admin/**").hasRole("ADMIN")
                        // Public read of published properties/projects
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/properties/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/projects", "/api/projects/**").permitAll()
                        // Uploaded cover images are public assets
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/uploads/**").permitAll()
                        // Public lead-generation forms (contact, quote, newsletter)
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                "/api/contact", "/api/quotes", "/api/newsletter", "/api/careers").permitAll()
                        // Health check. Render polls this on every deploy and keeps
                        // polling it afterwards, so it has to be reachable without a
                        // token - but only these paths, only for GET, and only with
                        // show-details: never, so an anonymous caller learns that the
                        // app is up and nothing else (not the database host, not which
                        // components are failing).
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")
                        .permitAll()
                        // Everything else under /actuator - metrics, prometheus,
                        // caches, info - is operational data: request rates, cache
                        // hit ratios, connection-pool depth, the JVM's own
                        // properties. Useful to an operator, and equally useful to
                        // someone deciding where this app is weakest. ADMIN only,
                        // stated here rather than left to anyRequest().authenticated(),
                        // so a widened management.endpoints.web.exposure.include can
                        // never quietly publish a new endpoint to any logged-in user.
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // API docs
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Admin-only management endpoints
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/properties/**").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/properties/**").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/properties/**").hasRole("ADMIN")
                        // Everything else requires a valid access token
                        .anyRequest().authenticated())
                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> allowedOrigins = corsProperties.allowedOrigins();

        // An unset or wildcarded origin list is a deployment mistake, and one
        // that only shows up later as CORS errors nobody can explain. Failing
        // at startup turns it into an obvious one.
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalStateException(
                    "app.cors.allowed-origins (CORS_ALLOWED_ORIGINS) must list at least one origin.");
        }
        if (allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException(
                    "app.cors.allowed-origins (CORS_ALLOWED_ORIGINS) must be an explicit list of origins; "
                            + "a wildcard lets any site on the internet read this API's responses in a "
                            + "visitor's browser. Got: " + allowedOrigins);
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        // Retry-After so the frontend can read it off a 429 instead of guessing.
        configuration.setExposedHeaders(List.of("Authorization", "Retry-After"));
        // False, because this API has no cookies and no session: the browser
        // sends an Authorization header, which is not a credential in the CORS
        // sense. Allowing credentials would let a page on an allowed origin
        // make authenticated requests with the browser's ambient state - a
        // capability nothing here needs, and the precondition for CSRF against
        // an API whose CSRF protection is switched off.
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * These filters are @Component-managed Spring beans (so they can be
     * @Autowired) but are wired explicitly into the Spring Security chain
     * above via addFilterBefore. Without disabling Boot's automatic
     * FilterRegistrationBean, they would otherwise also run a second time
     * as generic servlet filters on every request.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RateLimitingFilter> rateLimitFilterRegistration(RateLimitingFilter filter) {
        FilterRegistrationBean<RateLimitingFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
