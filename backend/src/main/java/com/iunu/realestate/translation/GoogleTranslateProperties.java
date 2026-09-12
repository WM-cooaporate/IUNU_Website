package com.iunu.realestate.translation;

/**
 * Bound from app.translation.google.*.
 *
 * The API key is server-side only: it arrives as GOOGLE_TRANSLATE_API_KEY and
 * is sent in the X-goog-api-key header, never in a URL (which would put it in
 * proxy and access logs) and never in anything the browser bundle can read.
 * An empty key disables translation rather than failing a save.
 *
 * @param apiKey          Google Cloud Translation API key; blank disables the feature.
 * @param baseUrl         API host, overridable so tests can point at a stub.
 * @param connectTimeoutMs TCP connect budget for one call.
 * @param readTimeoutMs   Response budget for one call. Translation happens on the
 *                        request thread before any DB connection is taken, so this
 *                        is the worst case a save can be slowed by per batch.
 */
@org.springframework.boot.context.properties.ConfigurationProperties(prefix = "app.translation.google")
public record GoogleTranslateProperties(
        String apiKey,
        String baseUrl,
        Integer connectTimeoutMs,
        Integer readTimeoutMs
) {
    public String baseUrlOrDefault() {
        return (baseUrl == null || baseUrl.isBlank()) ? "https://translation.googleapis.com" : baseUrl.trim();
    }

    public int connectTimeoutMsOrDefault() {
        return connectTimeoutMs == null ? 3000 : connectTimeoutMs;
    }

    public int readTimeoutMsOrDefault() {
        return readTimeoutMs == null ? 10000 : readTimeoutMs;
    }
}
