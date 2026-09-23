package com.iunu.realestate.config;

import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Request;
import io.sentry.protocol.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What leaves this process when Sentry is on (SENTRY_DSN set). The starter
 * already runs with {@code send-default-pii: false}; this is the second
 * layer, because "the SDK's default" is a promise about someone else's code.
 *
 * <p>Stripped from every event: credentials in headers, cookies, the request
 * body (a login or reset body carries a password or a token), the query
 * string, and the user's email and address. What is left - the exception, the
 * stack, the route, the request id - is what an error report needs.
 */
@Configuration
public class SentryConfig {

    static final Set<String> STRIPPED_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie", "x-edge-auth");

    @Bean
    public SentryOptions.BeforeSendCallback sentryBeforeSend() {
        return (event, hint) -> scrub(event);
    }

    static SentryEvent scrub(SentryEvent event) {
        Request request = event.getRequest();
        if (request != null) {
            request.setData(null);
            request.setCookies(null);
            request.setQueryString(null);
            if (request.getHeaders() != null) {
                Map<String, String> headers = new HashMap<>(request.getHeaders());
                headers.keySet().removeIf(name -> STRIPPED_HEADERS.contains(name.toLowerCase(Locale.ROOT)));
                request.setHeaders(headers);
            }
        }
        User user = event.getUser();
        if (user != null) {
            user.setEmail(null);
            user.setIpAddress(null);
            user.setUsername(null);
        }
        return event;
    }
}
