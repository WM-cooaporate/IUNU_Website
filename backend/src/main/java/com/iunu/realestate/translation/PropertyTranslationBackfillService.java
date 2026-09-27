package com.iunu.realestate.translation;

import com.iunu.realestate.dto.response.TranslationBackfillResponse;
import com.iunu.realestate.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

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

    /**
     * One backfill at a time. The job walks every property with a missing
     * Arabic field and makes a paid API call for each, so two of them running
     * concurrently translate the same rows twice and bill twice - and a script
     * firing the endpoint in a loop bills without limit. A second caller gets
     * 409 rather than queueing, because a queued duplicate is the same waste
     * deferred.
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public TranslationBackfillResponse backfill() {
        if (!translationService.isEnabled()) {
            return new TranslationBackfillResponse(0, 0, 0, false);
        }

        if (!running.compareAndSet(false, true)) {
            throw new ConflictException("A translation backfill is already running. Wait for it to finish.");
        }
        try {
            return runBackfill();
        } finally {
            // In a finally block so a failure mid-run does not wedge the flag
            // on and make the endpoint permanently return 409 until a restart.
            running.set(false);
        }
    }

    private TranslationBackfillResponse runBackfill() {
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
