package com.iunu.realestate.dto.response;

/**
 * @param scanned properties that had at least one missing Arabic field
 * @param updated properties that came back with at least one field filled
 * @param failed  properties the translator could not fill (left untouched)
 * @param enabled false when no API key is configured; nothing was scanned
 */
public record TranslationBackfillResponse(int scanned, int updated, int failed, boolean enabled) {
}
