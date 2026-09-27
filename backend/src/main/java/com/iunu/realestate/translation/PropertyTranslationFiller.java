package com.iunu.realestate.translation;

import com.iunu.realestate.dto.request.PropertyRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fills in the Arabic half of a project save.
 *
 * The rule, per field: an Arabic value the admin typed is kept exactly as
 * typed; a blank one with non-blank English is machine-translated; blank
 * English leaves Arabic null. All three fields go out in a single batch, so a
 * save costs one call to Google rather than three.
 *
 * This runs in the controller, outside any transaction, so no database
 * connection is held while waiting on the network.
 */
@Component
@RequiredArgsConstructor
public class PropertyTranslationFiller {

    /** Matches the title_ar / location_ar column width. */
    private static final int SHORT_FIELD_LIMIT = 400;

    private final TranslationService translationService;

    /** Returns a copy of the request with blank Arabic fields auto-translated from English. */
    public PropertyRequest fill(PropertyRequest request) {
        if (request == null) {
            return null;
        }

        boolean needsTitle = needsTranslation(request.titleAr(), request.title());
        boolean needsDescription = needsTranslation(request.descriptionAr(), request.description());
        boolean needsLocation = needsTranslation(request.locationAr(), request.location());

        if (!needsTitle && !needsDescription && !needsLocation) {
            return request;
        }

        List<String> sources = new ArrayList<>(3);
        if (needsTitle) sources.add(request.title());
        if (needsDescription) sources.add(request.description());
        if (needsLocation) sources.add(request.location());

        List<String> translated = translationService.translateEnToAr(sources);

        int next = 0;
        String titleAr = request.titleAr();
        String descriptionAr = request.descriptionAr();
        String locationAr = request.locationAr();
        if (needsTitle) titleAr = truncate(clean(valueAt(translated, next++)), SHORT_FIELD_LIMIT);
        if (needsDescription) descriptionAr = clean(valueAt(translated, next++));
        if (needsLocation) locationAr = truncate(clean(valueAt(translated, next)), SHORT_FIELD_LIMIT);

        return new PropertyRequest(
                request.title(),
                request.description(),
                request.type(),
                request.status(),
                request.location(),
                titleAr,
                descriptionAr,
                locationAr,
                request.area(),
                request.price(),
                request.coverImageUrl(),
                request.imageUrls(),
                request.published()
        );
    }

    private static boolean needsTranslation(String arabic, String english) {
        return (arabic == null || arabic.isBlank()) && english != null && !english.isBlank();
    }

    /** Defensive: the translator returns a short list only if it misbehaves, but a save must not 500 for it. */
    private static String valueAt(List<String> values, int index) {
        return (values != null && index < values.size()) ? values.get(index) : null;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Arabic runs longer than its English source often enough that a title near
     * the English 200-character limit can overshoot the 400-character Arabic
     * column. Losing the tail of a translation beats a 500 on save.
     */
    private static String truncate(String value, int limit) {
        return (value != null && value.length() > limit) ? value.substring(0, limit) : value;
    }
}
