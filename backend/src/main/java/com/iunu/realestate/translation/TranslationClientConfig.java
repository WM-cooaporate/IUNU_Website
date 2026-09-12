package com.iunu.realestate.translation;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * The HTTP client the translator uses.
 *
 * It is a separate builder rather than the application-wide one because the
 * timeouts here are deliberately short: this call sits on the request thread
 * of an admin save, and a slow Google is a slow "Save project" button.
 * Keeping the builder injectable is also what lets the unit test bind a
 * MockRestServiceServer to it instead of reaching the network.
 */
@Configuration
public class TranslationClientConfig {

    public static final String BUILDER = "translationRestClientBuilder";

    @Bean(BUILDER)
    public RestClient.Builder translationRestClientBuilder(GoogleTranslateProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMsOrDefault()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMsOrDefault()));
        return RestClient.builder().requestFactory(requestFactory);
    }

    @Bean
    public TranslationService translationService(
            @Qualifier(BUILDER) RestClient.Builder builder,
            GoogleTranslateProperties properties) {
        return new GoogleTranslationService(builder, properties);
    }
}
