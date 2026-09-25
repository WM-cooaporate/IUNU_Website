package com.iunu.realestate.dto.response;

import com.iunu.realestate.entity.Property;
import com.iunu.realestate.entity.PropertyStatus;
import com.iunu.realestate.entity.PropertyType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * What an anonymous visitor sees of a property: the listing content and
 * nothing about how it is managed.
 *
 * <p>{@link PropertyResponse} stays the admin shape. It adds {@code published}
 * (always true on a public read, so it only advertises that drafts exist) and
 * the {@code createdAt}/{@code updatedAt} audit timestamps, which no public
 * page uses. Keeping two records means a field added for the dashboard is
 * private unless someone deliberately adds it here too.
 */
public record PropertyPublicResponse(
        Long id,
        String title,
        String description,
        PropertyType type,
        PropertyStatus status,
        String location,
        // Arabic copies, null when translation is off or has not run yet.
        String titleAr,
        String descriptionAr,
        String locationAr,
        BigDecimal area,
        BigDecimal price,
        String coverImageUrl,
        List<String> imageUrls
) {
    public static PropertyPublicResponse from(Property property) {
        return new PropertyPublicResponse(
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
                // Copied inside the transaction: imageUrls is lazy and
                // open-in-view is off (see PropertyResponse.from).
                new ArrayList<>(property.getImageUrls())
        );
    }
}
