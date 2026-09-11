package com.iunu.realestate.dto.response;

/**
 * @param enabled false when no API key is configured; the three values are
 *                then null and the dashboard says so instead of showing an error.
 */
public record TranslationPreviewResponse(
        String titleAr,
        String descriptionAr,
        String locationAr,
        boolean enabled
) {
}
