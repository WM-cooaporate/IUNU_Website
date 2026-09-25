package com.iunu.realestate.service.impl;

import com.iunu.realestate.dto.request.PropertyRequest;
import com.iunu.realestate.dto.response.PropertyPublicResponse;
import com.iunu.realestate.dto.response.PropertyResponse;
import com.iunu.realestate.entity.AuditAction;
import com.iunu.realestate.entity.Property;
import com.iunu.realestate.entity.PropertyStatus;
import com.iunu.realestate.entity.PropertyType;
import com.iunu.realestate.exception.ResourceNotFoundException;
import com.iunu.realestate.repository.PropertyRepository;
import com.iunu.realestate.service.AuditLogService;
import com.iunu.realestate.service.PropertyService;
import com.iunu.realestate.service.ImageStorage;
import com.iunu.realestate.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Caching note: only the two <em>public</em> reads are cached, and every write
 * clears both caches completely.
 *
 * <p>Zero staleness after a write is a requirement, not a nicety - the admin
 * saves a property and immediately checks the public site. Evicting everything
 * rather than computing which listing pages a given property appears on is the
 * cheap, obviously-correct choice at this data volume.
 *
 * <p>The admin reads are never cached: they serve drafts, and an admin who
 * unpublishes something has to see it disappear on the next load.
 */
@Service
@RequiredArgsConstructor
public class PropertyServiceImpl implements PropertyService {

    private final PropertyRepository propertyRepository;
    private final ImageStorage imageStorage;
    private final AuditLogService auditLogService;

    /** Audit target type for every row this service writes. */
    private static final String AUDIT_TARGET = "PROPERTY";

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.PUBLIC_PROPERTY_LIST,
            key = "#type + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort")
    public Page<PropertyPublicResponse> listPublished(PropertyType type, Pageable pageable) {
        Page<Property> page = (type != null)
                ? propertyRepository.findByPublishedTrueAndType(type, pageable)
                : propertyRepository.findByPublishedTrue(pageable);
        return page.map(PropertyPublicResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    // No `unless` is needed for the missing case: a not-found property throws,
    // and Spring's cache abstraction never stores the result of a method that
    // threw. Only a real, published property is ever cached.
    @Cacheable(cacheNames = CacheConfig.PUBLIC_PROPERTY_BY_ID, key = "#id")
    public PropertyPublicResponse getPublishedById(Long id) {
        Property property = propertyRepository.findById(id)
                .filter(Property::isPublished)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));
        return PropertyPublicResponse.from(property);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PropertyResponse> listAllForAdmin(Pageable pageable) {
        return propertyRepository.findAll(pageable).map(PropertyResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public PropertyResponse getByIdForAdmin(Long id) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));
        return PropertyResponse.from(property);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.PUBLIC_PROPERTY_LIST, CacheConfig.PUBLIC_PROPERTY_BY_ID},
            allEntries = true)
    public PropertyResponse create(PropertyRequest request) {
        Property property = Property.builder()
                .title(request.title().trim())
                .description(request.description())
                .type(request.type())
                .status(request.status() != null ? request.status() : PropertyStatus.AVAILABLE)
                .location(request.location())
                // Already filled in by PropertyTranslationFiller at the
                // controller layer, so there is nothing to translate here -
                // this only normalizes blank to null.
                .titleAr(blankToNull(request.titleAr()))
                .descriptionAr(blankToNull(request.descriptionAr()))
                .locationAr(blankToNull(request.locationAr()))
                .area(request.area())
                .price(request.price())
                .coverImageUrl(request.coverImageUrl())
                .imageUrls(request.imageUrls() != null ? new ArrayList<>(request.imageUrls()) : new ArrayList<>())
                .published(request.published() == null || request.published())
                .build();

        propertyRepository.save(property);
        auditLogService.record(AuditAction.PROPERTY_CREATED, AUDIT_TARGET, property.getId(),
                "created; published " + property.isPublished());
        return PropertyResponse.from(property);
    }

    @Override
    @Transactional
    // Covers the publish/unpublish toggle and every image change too: both
    // arrive as an update, and an unpublished property must vanish from the
    // public listing on the very next request.
    @CacheEvict(cacheNames = {CacheConfig.PUBLIC_PROPERTY_LIST, CacheConfig.PUBLIC_PROPERTY_BY_ID},
            allEntries = true)
    public PropertyResponse update(Long id, PropertyRequest request) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));
        Set<String> previousImages = imageUrlsOf(property);
        Snapshot before = Snapshot.of(property);

        property.setTitle(request.title().trim());
        property.setDescription(request.description());
        property.setType(request.type());
        if (request.status() != null) {
            property.setStatus(request.status());
        }
        property.setLocation(request.location());
        property.setTitleAr(blankToNull(request.titleAr()));
        property.setDescriptionAr(blankToNull(request.descriptionAr()));
        property.setLocationAr(blankToNull(request.locationAr()));
        property.setArea(request.area());
        property.setPrice(request.price());
        property.setCoverImageUrl(request.coverImageUrl());
        if (request.imageUrls() != null) {
            property.setImageUrls(new ArrayList<>(request.imageUrls()));
        }
        if (request.published() != null) {
            property.setPublished(request.published());
        }

        propertyRepository.save(property);
        auditUpdate(before, Snapshot.of(property), id);
        previousImages.removeAll(imageUrlsOf(property));
        deleteUnusedImages(previousImages, id);
        return PropertyResponse.from(property);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.PUBLIC_PROPERTY_LIST, CacheConfig.PUBLIC_PROPERTY_BY_ID},
            allEntries = true)
    public void delete(Long id) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));
        Set<String> images = imageUrlsOf(property);
        propertyRepository.deleteById(id);
        auditLogService.record(AuditAction.PROPERTY_DELETED, AUDIT_TARGET, id, "deleted");
        deleteUnusedImages(images, id);
    }

    /**
     * One audit row per update. A change to {@code published} is the row's
     * action, since "who took this listing down" is the question most often
     * asked; any other field changes ride along in the summary.
     *
     * <p>The summary names fields, never their values - a description can be
     * a page of text and has no business being duplicated into the audit
     * table. The two exceptions are {@code published} and {@code status},
     * whose values are a closed set and are the useful part.
     */
    private void auditUpdate(Snapshot before, Snapshot after, Long id) {
        List<String> changes = new ArrayList<>();
        before.changedFields(after).forEach(field -> changes.add(field + " changed"));
        if (before.status() != after.status()) {
            changes.add("status " + before.status() + "\u2192" + after.status());
        }
        AuditAction action = AuditAction.PROPERTY_UPDATED;
        if (before.published() != after.published()) {
            changes.add("published " + before.published() + "\u2192" + after.published());
            action = after.published() ? AuditAction.PROPERTY_PUBLISHED : AuditAction.PROPERTY_UNPUBLISHED;
        }
        auditLogService.record(action, AUDIT_TARGET, id, changes.isEmpty() ? "saved; no changes" : String.join("; ", changes));
    }

    /** The fields an audit summary talks about, captured before and after an update. */
    private record Snapshot(String title, String description, PropertyType type, PropertyStatus status,
                            String location, String titleAr, String descriptionAr, String locationAr,
                            Object area, Object price, String coverImageUrl, List<String> imageUrls,
                            boolean published) {

        static Snapshot of(Property p) {
            return new Snapshot(p.getTitle(), p.getDescription(), p.getType(), p.getStatus(), p.getLocation(),
                    p.getTitleAr(), p.getDescriptionAr(), p.getLocationAr(), p.getArea(), p.getPrice(),
                    p.getCoverImageUrl(), p.getImageUrls() == null ? List.of() : List.copyOf(p.getImageUrls()),
                    p.isPublished());
        }

        List<String> changedFields(Snapshot other) {
            List<String> fields = new ArrayList<>();
            if (!Objects.equals(title, other.title)) fields.add("title");
            if (!Objects.equals(description, other.description)) fields.add("description");
            if (type != other.type) fields.add("type");
            if (!Objects.equals(location, other.location)) fields.add("location");
            if (!Objects.equals(titleAr, other.titleAr)
                    || !Objects.equals(descriptionAr, other.descriptionAr)
                    || !Objects.equals(locationAr, other.locationAr)) fields.add("arabic text");
            if (!sameNumber(area, other.area)) fields.add("area");
            if (!sameNumber(price, other.price)) fields.add("price");
            if (!Objects.equals(coverImageUrl, other.coverImageUrl)) fields.add("cover image");
            if (!Objects.equals(imageUrls, other.imageUrls)) fields.add("images");
            return fields;
        }

        /** BigDecimal equals() counts scale, so 1.0 and 1.00 would read as a change. */
        private static boolean sameNumber(Object a, Object b) {
            if (a instanceof java.math.BigDecimal x && b instanceof java.math.BigDecimal y) {
                return x.compareTo(y) == 0;
            }
            return Objects.equals(a, b);
        }
    }

    /**
     * Blank and absent mean the same thing for an Arabic field - "there is no
     * Arabic copy" - and storing "" instead of NULL would hide the row from
     * the backfill query, which looks for NULLs.
     */
    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private Set<String> imageUrlsOf(Property property) {
        Set<String> urls = new HashSet<>();
        if (property.getCoverImageUrl() != null) urls.add(property.getCoverImageUrl());
        if (property.getImageUrls() != null) urls.addAll(property.getImageUrls());
        return urls;
    }

    /**
     * Deletes the files behind {@code candidates}, but only those no other
     * property still points at.
     *
     * <p>Storage is content-addressed (the filename is the SHA-256 of the
     * bytes), so uploading the same photo to two properties yields <em>one</em>
     * file with two references. Deleting it because the property being edited
     * dropped it would blank the image on the other property. Hence the
     * per-URL check rather than "this property no longer uses it".
     *
     * <p>This used to load every property row - {@code findAll()} - on every
     * admin save and delete, which is a full table scan plus a second query per
     * row for its image collection, to answer a question two indexed lookups
     * answer. Now it is one cheap existence query per candidate URL, and only
     * for URLs that were actually removed (usually none).
     */
    private void deleteUnusedImages(Set<String> candidates, Long excludePropertyId) {
        candidates.stream()
                .filter(url -> !propertyRepository.isImageUsedByOtherProperty(url, excludePropertyId))
                .forEach(imageStorage::deleteIfStored);
    }
}
