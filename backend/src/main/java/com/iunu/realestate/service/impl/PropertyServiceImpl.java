package com.iunu.realestate.service.impl;

import com.iunu.realestate.dto.request.PropertyRequest;
import com.iunu.realestate.dto.response.PropertyResponse;
import com.iunu.realestate.entity.Property;
import com.iunu.realestate.entity.PropertyStatus;
import com.iunu.realestate.entity.PropertyType;
import com.iunu.realestate.exception.ResourceNotFoundException;
import com.iunu.realestate.repository.PropertyRepository;
import com.iunu.realestate.service.PropertyService;
import com.iunu.realestate.service.ImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PropertyServiceImpl implements PropertyService {

    private final PropertyRepository propertyRepository;
    private final ImageStorageService imageStorageService;

    @Override
    @Transactional(readOnly = true)
    public Page<PropertyResponse> listPublished(PropertyType type, Pageable pageable) {
        Page<Property> page = (type != null)
                ? propertyRepository.findByPublishedTrueAndType(type, pageable)
                : propertyRepository.findByPublishedTrue(pageable);
        return page.map(PropertyResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
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
    public PropertyResponse create(PropertyRequest request) {
        Property property = Property.builder()
                .title(request.title().trim())
                .description(request.description())
                .type(request.type())
                .status(request.status() != null ? request.status() : PropertyStatus.AVAILABLE)
                .location(request.location())
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
    public void delete(Long id) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));
        Set<String> images = imageUrlsOf(property);
        propertyRepository.deleteById(id);
        deleteUnusedImages(images, id);
    }

    private Set<String> imageUrlsOf(Property property) {
        Set<String> urls = new HashSet<>();
        if (property.getCoverImageUrl() != null) urls.add(property.getCoverImageUrl());
        if (property.getImageUrls() != null) urls.addAll(property.getImageUrls());
        return urls;
    }

    private void deleteUnusedImages(Set<String> candidates, Long ignoredPropertyId) {
        Set<String> activeImages = new HashSet<>();
        propertyRepository.findAll().stream()
                .filter(property -> !property.getId().equals(ignoredPropertyId))
                .forEach(property -> activeImages.addAll(imageUrlsOf(property)));
        candidates.removeAll(activeImages);
        candidates.forEach(imageStorageService::deleteIfStored);
    }
}
