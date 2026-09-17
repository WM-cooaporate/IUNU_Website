package com.iunu.realestate.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Which address the per-IP limits key on.
 *
 * <p>Both ways of getting this wrong are silent and neither shows up in
 * testing against localhost, which is why they get tests: trusting too little
 * puts every visitor in one bucket (the proxy's), trusting too much lets a
 * caller choose their own bucket with a forged header.
 */
@DisplayName("Client IP resolution")
class ClientIpResolverTest {

    private static final String SOCKET_ADDRESS = "10.0.0.5";

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(SOCKET_ADDRESS);
        return request;
    }

    private static ClientIpResolver resolver(String mode, int hops) {
        return new ClientIpResolver(mode, hops, false, "");
    }

    private static ClientIpResolver resolver(String mode, int hops, String trustedProxies) {
        return new ClientIpResolver(mode, hops, false, trustedProxies);
    }

    @Nested
    @DisplayName("remote-addr mode")
    class RemoteAddrMode {

        @Test
        @DisplayName("uses the socket address and ignores any forwarding header")
        void ignoresHeaders() {
            MockHttpServletRequest request = request();
            request.addHeader("X-Forwarded-For", "1.2.3.4");
            request.addHeader("CF-Connecting-IP", "5.6.7.8");

            assertThat(resolver("remote-addr", 1).resolve(request)).isEqualTo(SOCKET_ADDRESS);
        }
    }

    @Nested
    @DisplayName("x-forwarded-for mode")
    class ForwardedForMode {

        /**
         * The core of the whole class. A client can send its own
         * X-Forwarded-For and the proxy appends to it, so with one trusted hop
         * the real address is the LAST entry - everything to its left is text
         * the caller chose.
         */
        @Test
        @DisplayName("takes the right-hand hop, not the caller-controlled left")
        void spoofedLeftmostEntryIsIgnored() {
            MockHttpServletRequest request = request();
            request.addHeader("X-Forwarded-For", "6.6.6.6, 203.0.113.9");

            assertThat(resolver("x-forwarded-for", 1).resolve(request)).isEqualTo("203.0.113.9");
        }

        @Test
        @DisplayName("with two trusted hops, reads one entry further left")
        void honoursConfiguredHopCount() {
            MockHttpServletRequest request = request();
            // client-forged , real client , cloudflare
            request.addHeader("X-Forwarded-For", "6.6.6.6, 203.0.113.9, 172.68.1.1");

            assertThat(resolver("x-forwarded-for", 2).resolve(request)).isEqualTo("203.0.113.9");
        }

        @Test
        @DisplayName("a single-entry header is the client when one hop is trusted")
        void singleEntry() {
            MockHttpServletRequest request = request();
            request.addHeader("X-Forwarded-For", "203.0.113.9");

            assertThat(resolver("x-forwarded-for", 1).resolve(request)).isEqualTo("203.0.113.9");
        }

        @Test
        @DisplayName("falls back to the socket address when the header is absent")
        void missingHeaderFallsBack() {
            assertThat(resolver("x-forwarded-for", 1).resolve(request())).isEqualTo(SOCKET_ADDRESS);
        }

        @Test
        @DisplayName("falls back to the socket address when the header is blank")
        void blankHeaderFallsBack() {
            MockHttpServletRequest request = request();
            request.addHeader("X-Forwarded-For", "   ");

            assertThat(resolver("x-forwarded-for", 1).resolve(request)).isEqualTo(SOCKET_ADDRESS);
        }

        /**
         * Fewer entries than configured hops means the header is not the shape
         * this deployment expects. The leftmost entry is closest to "the
         * client" but is also the one a caller can write, so it is not used.
         */
        @Test
        @DisplayName("a header shorter than the hop count falls back rather than reading the left")
        void tooFewHopsFallsBack() {
            MockHttpServletRequest request = request();
            request.addHeader("X-Forwarded-For", "6.6.6.6");

            assertThat(resolver("x-forwarded-for", 3).resolve(request)).isEqualTo(SOCKET_ADDRESS);
        }
    }

    @Nested
    @DisplayName("cloudflare mode")
    class CloudflareMode {

        @Test
        @DisplayName("uses CF-Connecting-IP")
        void usesCloudflareHeader() {
            MockHttpServletRequest request = request();
            request.addHeader("CF-Connecting-IP", "198.51.100.7");
            request.addHeader("X-Forwarded-For", "6.6.6.6");

            assertThat(resolver("cloudflare", 1).resolve(request)).isEqualTo("198.51.100.7");
        }

        @Test
        @DisplayName("falls back to the socket address when the header is absent")
        void missingHeaderFallsBack() {
            assertThat(resolver("cloudflare", 1).resolve(request())).isEqualTo(SOCKET_ADDRESS);
        }
    }

    @Nested
    @DisplayName("configuration")
    class Configuration {

        /**
         * The property this replaced. An existing deployment sets it, and a
         * release that silently stopped honouring it would collapse every
         * client behind Render's proxy into one bucket without a single error.
         */
        @Test
        @DisplayName("the legacy trust-forwarded-header flag still selects x-forwarded-for")
        void legacyFlagIsHonoured() {
            ClientIpResolver legacy = new ClientIpResolver("", 1, true, "");
            MockHttpServletRequest request = request();
            request.addHeader("X-Forwarded-For", "6.6.6.6, 203.0.113.9");

            assertThat(legacy.mode()).isEqualTo(ClientIpResolver.Mode.X_FORWARDED_FOR_MODE);
            assertThat(legacy.resolve(request)).isEqualTo("203.0.113.9");
        }

        @Test
        @DisplayName("an explicit mode overrides the legacy flag")
        void explicitModeWins() {
            ClientIpResolver resolver = new ClientIpResolver("remote-addr", 1, true, "");
            MockHttpServletRequest request = request();
            request.addHeader("X-Forwarded-For", "6.6.6.6");

            assertThat(resolver.resolve(request)).isEqualTo(SOCKET_ADDRESS);
        }

        /** A typo here would otherwise degrade to a default nobody chose. */
        @Test
        @DisplayName("an unrecognised mode fails at startup rather than guessing")
        void unknownModeFailsFast() {
            assertThatThrownBy(() -> new ClientIpResolver("x-real-ip", 1, false, ""))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CLIENT_IP_MODE");
        }

        @Test
        @DisplayName("a hop count below 1 is clamped, never treated as 'read the leftmost'")
        void hopCountIsClamped() {
            MockHttpServletRequest request = request();
            request.addHeader("X-Forwarded-For", "6.6.6.6, 203.0.113.9");

            assertThat(resolver("x-forwarded-for", 0).resolve(request)).isEqualTo("203.0.113.9");
        }
    }

    /**
     * The check that makes a forwarding header trustworthy at all.
     *
     * <p>"The last X-Forwarded-For entry is the real client" holds only if a
     * proxy appended it. A request that reaches the app directly - over the
     * platform's own *.onrender.com URL, say - carries whatever the caller
     * typed, end to end, so without this the caller picks their own rate-limit
     * bucket and the limit is decoration. The socket address is the one part of
     * a request nobody can forge, so it is what decides.
     */
    @Nested
    @DisplayName("trusted-proxy allowlist")
    class TrustedProxies {

        @Test
        @DisplayName("a forwarding header from an untrusted peer is ignored")
        void untrustedPeerCannotForgeTheHeader() {
            MockHttpServletRequest request = request(); // socket address 10.0.0.5
            request.addHeader("X-Forwarded-For", "6.6.6.6");

            ClientIpResolver resolver = resolver("x-forwarded-for", 1, "172.16.0.0/12");

            assertThat(resolver.resolve(request)).isEqualTo(SOCKET_ADDRESS);
        }

        @Test
        @DisplayName("a forwarding header from the real proxy is honoured")
        void trustedPeerIsBelieved() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.4.1.9");
            request.addHeader("X-Forwarded-For", "6.6.6.6, 203.0.113.9");

            ClientIpResolver resolver = resolver("x-forwarded-for", 1, "10.0.0.0/8");

            assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
        }

        @Test
        @DisplayName("CF-Connecting-IP from an untrusted peer is ignored too")
        void cloudflareHeaderAlsoNeedsATrustedPeer() {
            MockHttpServletRequest request = request();
            request.addHeader("CF-Connecting-IP", "6.6.6.6");

            assertThat(resolver("cloudflare", 1, "172.16.0.0/12").resolve(request))
                    .isEqualTo(SOCKET_ADDRESS);
        }

        @Test
        @DisplayName("several blocks are accepted, and any one matching is enough")
        void multipleBlocks() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("172.20.0.3");
            request.addHeader("X-Forwarded-For", "203.0.113.9");

            assertThat(resolver("x-forwarded-for", 1, "10.0.0.0/8, 172.16.0.0/12").resolve(request))
                    .isEqualTo("203.0.113.9");
        }

        @Test
        @DisplayName("a bare address is an exact match, not a range")
        void bareAddressIsExact() {
            MockHttpServletRequest neighbour = new MockHttpServletRequest();
            neighbour.setRemoteAddr("10.0.0.6");
            neighbour.addHeader("X-Forwarded-For", "203.0.113.9");

            // 10.0.0.5 is allowed; 10.0.0.6 is not, even though it is adjacent.
            assertThat(resolver("x-forwarded-for", 1, "10.0.0.5").resolve(neighbour))
                    .isEqualTo("10.0.0.6");
        }

        @Test
        @DisplayName("an IPv4 block never matches an IPv6 peer")
        void addressFamiliesDoNotMix() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("::1");
            request.addHeader("X-Forwarded-For", "6.6.6.6");

            assertThat(resolver("x-forwarded-for", 1, "0.0.0.0/0").resolve(request)).isEqualTo("::1");
        }

        @Test
        @DisplayName("a malformed entry fails at startup rather than silently trusting nothing")
        void malformedEntryFailsFast() {
            assertThatThrownBy(() -> resolver("x-forwarded-for", 1, "not-an-address"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TRUSTED_PROXIES");
            assertThatThrownBy(() -> resolver("x-forwarded-for", 1, "10.0.0.0/99"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TRUSTED_PROXIES");
        }
    }
}
