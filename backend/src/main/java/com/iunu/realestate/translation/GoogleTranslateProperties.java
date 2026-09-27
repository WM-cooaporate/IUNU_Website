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
 * @param dailyCharLimit  Characters per UTC day, across every caller. Google bills
 *                        per character, so this is the ceiling on what a loop over
 *                        the preview or backfill endpoint can cost. 0 or less means
 *                        unlimited - appropriate only when a real quota is set in
 *                        the Google Cloud Console. See TranslationBudget.
 */
@org.springframework.boot.context.properties.ConfigurationProperties(prefix = "app.translation.google")
public record GoogleTranslateProperties(
        String apiKey,
        String baseUrl,
        Integer connectTimeoutMs,
        Integer readTimeoutMs,
        Long dailyCharLimit
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

    public long dailyCharLimitOrDefault() {
        return dailyCharLimit == null ? 200_000L : dailyCharLimit;
    }
}
