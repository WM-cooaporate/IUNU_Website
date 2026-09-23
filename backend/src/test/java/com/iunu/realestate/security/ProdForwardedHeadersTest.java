package com.iunu.realestate.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N6: under the prod profile, server.forward-headers-strategy=framework made
 * Spring's ForwardedHeaderFilter replace the client address with the leftmost
 * - caller-chosen - X-Forwarded-For entry before ClientIpResolver ever saw the
 * request, so every per-IP limit could be bypassed with one header.
 *
 * <p>The valve and the filter only exist inside a real servlet container, so
 * the behaviour itself is proven by the attack lab (abuse.js, BEHIND_PROXY=yes
 * against the prod profile). This pins the configuration that makes it hold.
 */
@DisplayName("Prod forwarded-header handling")
class ProdForwardedHeadersTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> server() throws Exception {
        try (InputStream in = ProdForwardedHeadersTest.class.getResourceAsStream("/application-prod.yml")) {
            Map<String, Object> root = new Yaml().load(in);
            return (Map<String, Object>) root.get("server");
        }
    }

    @Test
    @DisplayName("never uses Spring's ForwardedHeaderFilter, which trusts the leftmost X-Forwarded-For")
    void notFrameworkStrategy() throws Exception {
        assertThat(server().get("forward-headers-strategy")).isEqualTo("native");
    }

    @Test
    @DisplayName("Tomcat's valve handles scheme and host only, never the client address")
    @SuppressWarnings("unchecked")
    void valveLeavesClientAddressAlone() throws Exception {
        Map<String, Object> remoteIp = (Map<String, Object>) ((Map<String, Object>) server().get("tomcat")).get("remoteip");
        assertThat((String) remoteIp.get("remote-ip-header")).isEmpty();
        assertThat(remoteIp.get("protocol-header")).isEqualTo("X-Forwarded-Proto");
    }
}
