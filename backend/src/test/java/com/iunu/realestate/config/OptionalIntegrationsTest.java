package com.iunu.realestate.config;

import io.sentry.SentryEvent;
import io.sentry.protocol.Request;
import io.sentry.protocol.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Optional monitoring integrations")
class OptionalIntegrationsTest {

    private final OptionalIntegrationsEnvironmentPostProcessor processor =
            new OptionalIntegrationsEnvironmentPostProcessor();

    private MockEnvironment process(MockEnvironment environment) {
        processor.postProcessEnvironment(environment, new SpringApplication());
        return environment;
    }

    @Test
    @DisplayName("with nothing set, OTLP and Sentry are both off")
    void everythingOffByDefault() {
        MockEnvironment environment = process(new MockEnvironment());
        assertThat(environment.getProperty("management.otlp.metrics.export.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("sentry.enabled")).isEqualTo("false");
    }

    @Test
    @DisplayName("blank values count as unset")
    void blankIsOff() {
        MockEnvironment environment = process(new MockEnvironment()
                .withProperty("OTLP_METRICS_URL", "  ")
                .withProperty("sentry.dsn", ""));
        assertThat(environment.getProperty("management.otlp.metrics.export.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("sentry.enabled")).isEqualTo("false");
    }

    @Test
    @DisplayName("OTLP turns on with a URL, and takes Grafana-style URL-encoded headers")
    void otlpOnWithUrl() {
        MockEnvironment environment = process(new MockEnvironment()
                .withProperty("OTLP_METRICS_URL", "https://otlp.example.net/otlp/v1/metrics")
                .withProperty("OTLP_METRICS_HEADERS", "Authorization=Basic%20abc123=,X-Scope=tenant"));
        assertThat(environment.getProperty("management.otlp.metrics.export.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("management.otlp.metrics.export.url"))
                .isEqualTo("https://otlp.example.net/otlp/v1/metrics");
        assertThat(environment.getProperty("management.otlp.metrics.export.headers.Authorization"))
                .isEqualTo("Basic abc123=");
        assertThat(environment.getProperty("management.otlp.metrics.export.headers.X-Scope")).isEqualTo("tenant");
    }

    @Test
    @DisplayName("Sentry is left alone when a DSN is set")
    void sentryOnWithDsn() {
        MockEnvironment environment = process(new MockEnvironment()
                .withProperty("sentry.dsn", "https://key@o0.ingest.example.io/1"));
        assertThat(environment.getProperty("sentry.enabled")).isNull();
    }

    @Test
    @DisplayName("an explicit setting elsewhere wins over this processor")
    void explicitSettingWins() {
        MockEnvironment environment = process(new MockEnvironment()
                .withProperty("management.otlp.metrics.export.enabled", "true"));
        assertThat(environment.getProperty("management.otlp.metrics.export.enabled")).isEqualTo("true");
    }

    @Test
    @DisplayName("Sentry events leave without credentials, cookies, bodies or identity")
    void sentryEventsAreScrubbed() {
        SentryEvent event = new SentryEvent();
        Request request = new Request();
        request.setHeaders(Map.of(
                "Authorization", "Bearer eyJhbGciOi.secret",
                "Cookie", "session=abc",
                "X-Edge-Auth", "edge-secret",
                "X-Request-Id", "3f2c1b9e-8d4a-4c21-9f0e-2b7a6c5d4e3f"));
        request.setData("{\"password\":\"hunter2\"}");
        request.setQueryString("token=reset-token");
        request.setCookies("session=abc");
        event.setRequest(request);
        User user = new User();
        user.setEmail("admin@iunu-eg.com");
        user.setIpAddress("198.51.100.7");
        event.setUser(user);

        SentryEvent scrubbed = SentryConfig.scrub(event);

        assertThat(scrubbed.getRequest().getHeaders()).containsOnlyKeys("X-Request-Id");
        assertThat(scrubbed.getRequest().getData()).isNull();
        assertThat(scrubbed.getRequest().getQueryString()).isNull();
        assertThat(scrubbed.getRequest().getCookies()).isNull();
        assertThat(scrubbed.getUser().getEmail()).isNull();
        assertThat(scrubbed.getUser().getIpAddress()).isNull();
    }
}
