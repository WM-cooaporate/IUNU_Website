package com.iunu.realestate.service.impl;

import com.iunu.realestate.dto.request.PropertyRequest;
import com.iunu.realestate.dto.response.PropertyResponse;
import com.iunu.realestate.entity.Property;
import com.iunu.realestate.entity.PropertyStatus;
import com.iunu.realestate.entity.PropertyType;
import com.iunu.realestate.exception.ResourceNotFoundException;
import com.iunu.realestate.repository.PropertyRepository;
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

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.PUBLIC_PROPERTY_LIST,
            key = "#type + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort")
    public Page<PropertyResponse> listPublished(PropertyType type, Pageable pageable) {
        Page<Property> page = (type != null)
                ? propertyRepository.findByPublishedTrueAndType(type, pageable)
                : propertyRepository.findByPublishedTrue(pageable);
        return page.map(PropertyResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    // No `unless` is needed for the missing case: a not-found property throws,
    // and Spring's cache abstraction never stores the result of a method that
    // threw. Only a real, published property is ever cached.
    @Cacheable(cacheNames = CacheConfig.PUBLIC_PROPERTY_BY_ID, key = "#id")
    public PropertyResponse getPublishedById(Long id) {
        Property property = propertyRepository.findById(id)
                .filter(Property::isPublished)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));
        return PropertyResponse.from(property);
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
        deleteUnusedImages(images, id);
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
