package com.iunu.realestate.security.events;

import ch.qos.logback.classic.Level;
import com.iunu.realestate.security.ClientIpResolver;
import com.iunu.realestate.support.LogCapture;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecurityEvents")
class SecurityEventsTest {

    private SimpleMeterRegistry registry;
    private SecurityEvents events;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        events = new SecurityEvents(registry, new ClientIpResolver("", 1, false, ""), 16);
    }

    private double count(SecurityEventType type) {
        return registry.get(SecurityEvents.METRIC).tag("type", type.name()).counter().count();
    }

    @Test
    @DisplayName("tags come from the enum only, whatever the caller passes")
    void tagsComeOnlyFromTheEnum() {
        for (int i = 0; i < 500; i++) {
            events.record(SecurityEventType.RATE_LIMITED, (long) i, "user" + i + "@example.com",
                    "10.0." + (i / 250) + "." + (i % 250),
                    Map.of("path", "/api/" + i, "bucket-" + i, "value" + i));
        }

        Set<String> allowed = Arrays.stream(SecurityEventType.values()).map(Enum::name).collect(Collectors.toSet());
        var counters = registry.find(SecurityEvents.METRIC).counters();
        // One series per enum constant, and not one more - 500 distinct paths,
        // emails, addresses and detail keys created no new time series.
        assertThat(counters).hasSize(SecurityEventType.values().length);
        for (Counter counter : counters) {
            assertThat(counter.getId().getTags()).hasSize(1);
            assertThat(allowed).contains(counter.getId().getTag("type"));
        }
    }

    @Test
    @DisplayName("the sampling cache stays bounded, and the counter still sees every event")
    void samplingIsBoundedAndCountingIsNot() {
        for (int i = 0; i < 2000; i++) {
            events.record(SecurityEventType.ACCESS_DENIED, null, null, "10.1." + (i / 250) + "." + (i % 250), null);
        }

        assertThat(events.sampledClientCount()).isLessThanOrEqualTo(16);
        assertThat(count(SecurityEventType.ACCESS_DENIED)).isEqualTo(2000);
    }

    @Test
    @DisplayName("a noisy type logs once per client per minute; the counter counts all of them")
    void noisyTypesAreSampledPerClient() {
        try (LogCapture capture = new LogCapture(SecurityEvents.LOGGER_NAME)) {
            for (int i = 0; i < 50; i++) {
                events.record(SecurityEventType.TOKEN_INVALID, null, null, "10.2.0.1", Map.of("reason", "expired"));
            }
            events.record(SecurityEventType.TOKEN_INVALID, null, null, "10.2.0.2", Map.of("reason", "expired"));

            assertThat(capture.messages()).hasSize(2);
        }
        assertThat(count(SecurityEventType.TOKEN_INVALID)).isEqualTo(51);
    }

    @Test
    @DisplayName("an event a human must see is never sampled and logs at WARN")
    void criticalTypesAlwaysLogAtWarn() {
        try (LogCapture capture = new LogCapture(SecurityEvents.LOGGER_NAME)) {
            for (int i = 0; i < 3; i++) {
                events.record(SecurityEventType.REFRESH_REUSE_DETECTED, 7L, "owner@iunu-eg.com", "10.3.0.1", null);
            }
            assertThat(capture.events()).hasSize(3)
                    .allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
        }
    }

    @Test
    @DisplayName("emails are masked in the log line")
    void emailsAreMasked() {
        try (LogCapture capture = new LogCapture(SecurityEvents.LOGGER_NAME)) {
            events.record(SecurityEventType.PASSWORD_CHANGED, 1L, "michael@iunu-eg.com", "10.4.0.1", null);

            String line = capture.messages().get(0);
            assertThat(line).contains("m***@iunu-eg.com").doesNotContain("michael");
        }
    }

    @Test
    @DisplayName("newline injection in detail values cannot forge a log line")
    void controlCharactersAreNeutralised() {
        try (LogCapture capture = new LogCapture(SecurityEvents.LOGGER_NAME)) {
            events.record(SecurityEventType.UPLOAD_REJECTED, null, null, "10.5.0.1\r\nINFO forged",
                    Map.of("reason", "x\n2026-09-23 INFO Admin login succeeded \u0085\u0000end"));

            String line = capture.messages().get(0);
            assertThat(line).doesNotContain("\n").doesNotContain("\r")
                    .doesNotContain(" ").doesNotContain("\u0085").doesNotContain("\u0000");
        }
    }

    @Test
    @DisplayName("very long detail values are truncated")
    void longValuesAreTruncated() {
        try (LogCapture capture = new LogCapture(SecurityEvents.LOGGER_NAME)) {
            events.record(SecurityEventType.RATE_LIMITED, null, null, "10.6.0.1", Map.of("path", "a".repeat(5000)));
            assertThat(capture.messages().get(0).length()).isLessThan(600);
        }
    }
}
