package com.iunu.realestate.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Turns the optional monitoring integrations on only when their variable is
 * set - the same rule as GOOGLE_TRANSLATE_API_KEY. Blank means off: no startup
 * failure, no background thread, no outbound call.
 *
 * <p>Needed because neither integration behaves that way on its own:
 *
 * <ul>
 *   <li><strong>OTLP metrics</strong> are on by default once
 *       micrometer-registry-otlp is on the classpath, and push to
 *       {@code localhost:4318} every minute - an outbound call, and a WARN in
 *       the log each time it fails. Here they are enabled only when
 *       {@code OTLP_METRICS_URL} is set, with headers from
 *       {@code OTLP_METRICS_HEADERS}.</li>
 *   <li><strong>Sentry</strong>'s auto-configuration is conditional on
 *       {@code sentry.dsn} being <em>present</em>, and a blank
 *       {@code SENTRY_DSN} is present. Sentry then treats the blank DSN as
 *       "disabled" - but that is its behaviour to change, so this also sets
 *       {@code sentry.enabled=false} explicitly.</li>
 * </ul>
 *
 * <p>Runs last, so it sees every other property source, and adds its own at
 * the lowest precedence below those it reads - an explicit
 * {@code management.otlp.metrics.export.enabled} elsewhere still wins.
 */
public class OptionalIntegrationsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String SOURCE_NAME = "iunuOptionalIntegrations";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> properties = new HashMap<>();

        String otlpUrl = environment.getProperty("OTLP_METRICS_URL", "");
        if (otlpUrl.isBlank()) {
            properties.put("management.otlp.metrics.export.enabled", "false");
        } else {
            properties.put("management.otlp.metrics.export.enabled", "true");
            properties.put("management.otlp.metrics.export.url", otlpUrl.trim());
            parseHeaders(environment.getProperty("OTLP_METRICS_HEADERS", ""))
                    .forEach((name, value) -> properties.put("management.otlp.metrics.export.headers." + name, value));
        }

        String sentryDsn = environment.getProperty("sentry.dsn", "");
        if (sentryDsn.isBlank()) {
            properties.put("sentry.enabled", "false");
        }

        environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, properties));
    }

    /**
     * {@code OTEL_EXPORTER_OTLP_HEADERS} format: {@code name=value,name2=value2},
     * values URL-encoded - which is how Grafana Cloud hands out its
     * {@code Authorization=Basic%20...} line, so it can be pasted as-is.
     */
    static Map<String, String> parseHeaders(String raw) {
        Map<String, String> headers = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return headers;
        }
        for (String pair : raw.split(",")) {
            int equals = pair.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String name = pair.substring(0, equals).trim();
            String value = URLDecoder.decode(pair.substring(equals + 1).trim(), StandardCharsets.UTF_8);
            if (!name.isEmpty()) {
                headers.put(name, value);
            }
        }
        return headers;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
