package com.iunu.realestate.dto.response;

import com.iunu.realestate.entity.Property;
import com.iunu.realestate.entity.PropertyStatus;
import com.iunu.realestate.entity.PropertyType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record PropertyResponse(
        Long id,
        String title,
        String description,
        PropertyType type,
        PropertyStatus status,
        String location,
        // Arabic copies, null when translation is off or has not run yet.
        // The site picks between these and the English above by language.
        String titleAr,
        String descriptionAr,
        String locationAr,
        BigDecimal area,
        BigDecimal price,
        String coverImageUrl,
        List<String> imageUrls,
        boolean published,
        Instant createdAt,
        Instant updatedAt
) {
    public static PropertyResponse from(Property property) {
        return new PropertyResponse(
                property.getId(),
                property.getTitle(),
                property.getDescription(),
                property.getType(),
                property.getStatus(),
                property.getLocation(),
                property.getTitleAr(),
                property.getDescriptionAr(),
                property.getLocationAr(),
                property.getArea(),
                property.getPrice(),
                property.getCoverImageUrl(),
                // Copy while still inside the transaction: imageUrls is a lazy
                // collection and open-in-view is disabled, so the Hibernate
                // session is gone by the time Jackson serializes the response.
                new ArrayList<>(property.getImageUrls()),
                property.isPublished(),
                property.getCreatedAt(),
                property.getUpdatedAt()
        );
    }
}
