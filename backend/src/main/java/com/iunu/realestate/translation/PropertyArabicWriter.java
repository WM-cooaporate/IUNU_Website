package com.iunu.realestate.translation;

import com.iunu.realestate.entity.Property;
import com.iunu.realestate.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * The two short database steps of the backfill, kept on their own bean so the
 * orchestration in PropertyTranslationBackfillService can call them through
 * the Spring proxy. A self-invoked @Transactional method skips the proxy
 * entirely, which is exactly how "why is this not transactional?" bugs happen.
 *
 * Nothing here talks to the network: the translation happens between these two
 * calls, with no transaction open and no connection held.
 */
@Component
@RequiredArgsConstructor
public class PropertyArabicWriter {

    /** Matches the title_ar / location_ar column width. */
    private static final int SHORT_FIELD_LIMIT = 400;

    private final PropertyRepository propertyRepository;

    @Transactional(readOnly = true)
    public List<Long> idsMissingArabic() {
        return propertyRepository.findIdsMissingArabic();
    }

    /** The English a property still needs Arabic for, or empty if it no longer needs any. */
    @Transactional(readOnly = true)
    public Optional<MissingArabic> readMissing(Long id) {
        return propertyRepository.findById(id).map(property -> new MissingArabic(
                id,
                isBlank(property.getTitleAr()) ? blankToNull(property.getTitle()) : null,
                isBlank(property.getDescriptionAr()) ? blankToNull(property.getDescription()) : null,
                isBlank(property.getLocationAr()) ? blankToNull(property.getLocation()) : null));
    }

    /**
     * Writes only the fields that are still missing. Re-checking inside the
     * transaction is what keeps a backfill from clobbering Arabic an admin
     * edited by hand while the translation was in flight.
     *
     * @return true when at least one field was written
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean fillMissing(Long id, String titleAr, String descriptionAr, String locationAr) {
        Property property = propertyRepository.findById(id).orElse(null);
        if (property == null) {
            return false;
        }
        boolean changed = false;
        if (isBlank(property.getTitleAr()) && !isBlank(titleAr)) {
            property.setTitleAr(truncate(titleAr.trim(), SHORT_FIELD_LIMIT));
            changed = true;
        }
        if (isBlank(property.getDescriptionAr()) && !isBlank(descriptionAr)) {
            property.setDescriptionAr(descriptionAr.trim());
            changed = true;
        }
        if (isBlank(property.getLocationAr()) && !isBlank(locationAr)) {
            property.setLocationAr(truncate(locationAr.trim(), SHORT_FIELD_LIMIT));
            changed = true;
        }
        if (changed) {
            propertyRepository.save(property);
        }
        return changed;
    }

    /** The English text still awaiting an Arabic copy, per field; null means "nothing to do". */
    public record MissingArabic(Long id, String title, String description, String location) {
        public boolean isEmpty() {
            return title == null && description == null && location == null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private static String truncate(String value, int limit) {
        return value.length() > limit ? value.substring(0, limit) : value;
    }
}
