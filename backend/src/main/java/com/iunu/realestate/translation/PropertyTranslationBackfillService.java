package com.iunu.realestate.translation;

import com.iunu.realestate.dto.response.TranslationBackfillResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fills in the Arabic for projects that were created before auto-translation
 * existed, or that were saved while it was switched off.
 *
 * Deliberately not @Transactional: one property at a time is read, translated
 * over the network, then written back in its own short transaction, so a slow
 * Google never holds a database connection and a failure partway through keeps
 * whatever was already filled.
 */
@Service
@RequiredArgsConstructor
public class PropertyTranslationBackfillService {

    private static final Logger log = LoggerFactory.getLogger(PropertyTranslationBackfillService.class);

    private final PropertyArabicWriter writer;
    private final TranslationService translationService;

    public TranslationBackfillResponse backfill() {
        if (!translationService.isEnabled()) {
            return new TranslationBackfillResponse(0, 0, 0, false);
        }

        List<Long> ids = writer.idsMissingArabic();
        int updated = 0;
        int failed = 0;

        for (Long id : ids) {
            Optional<PropertyArabicWriter.MissingArabic> maybeMissing = writer.readMissing(id);
            if (maybeMissing.isEmpty() || maybeMissing.get().isEmpty()) {
                continue; // filled in by someone else since the scan
            }
            PropertyArabicWriter.MissingArabic missing = maybeMissing.get();

            List<String> sources = new ArrayList<>(3);
            sources.add(missing.title());
            sources.add(missing.description());
            sources.add(missing.location());

            List<String> translated = translationService.translateEnToAr(sources);
            String titleAr = valueAt(translated, 0);
            String descriptionAr = valueAt(translated, 1);
            String locationAr = valueAt(translated, 2);

            if (titleAr == null && descriptionAr == null && locationAr == null) {
                failed++;
                continue;
            }
            if (writer.fillMissing(id, titleAr, descriptionAr, locationAr)) {
                updated++;
            }
        }

        log.info("Arabic backfill finished: scanned {}, updated {}, failed {}", ids.size(), updated, failed);
        return new TranslationBackfillResponse(ids.size(), updated, failed, true);
    }

    private static String valueAt(List<String> values, int index) {
        return (values != null && index < values.size()) ? values.get(index) : null;
    }
}
