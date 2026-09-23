package com.iunu.realestate.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the prod profile's forward-headers setting that ForwardedHeadersBypassTest
 * proves safe. "framework" and "native" both rewrite the client address from
 * X-Forwarded-For (N6); the scheme is handled by ForwardedProtoFilter instead.
 */
@DisplayName("Prod forwarded-header handling")
class ProdForwardedHeadersTest {

    @Test
    @DisplayName("the prod profile leaves X-Forwarded-For to ClientIpResolver alone")
    @SuppressWarnings("unchecked")
    void prodStrategyIsNone() throws Exception {
        try (InputStream in = ProdForwardedHeadersTest.class.getResourceAsStream("/application-prod.yml")) {
            Map<String, Object> server = (Map<String, Object>) ((Map<String, Object>) new Yaml().load(in)).get("server");
            assertThat(server.get("forward-headers-strategy")).isEqualTo("none");
            assertThat(server).doesNotContainKey("tomcat");
        }
    }
}
