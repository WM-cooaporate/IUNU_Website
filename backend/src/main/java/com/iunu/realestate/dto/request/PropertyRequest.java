package com.iunu.realestate.dto.request;

import com.iunu.realestate.entity.PropertyStatus;
import com.iunu.realestate.entity.PropertyType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record PropertyRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 200)
        String title,

        @Size(max = 20000)
        String description,

        @NotNull(message = "Property type is required")
        PropertyType type,

        PropertyStatus status,

        @Size(max = 200)
        String location,

        // Optional Arabic copies. Blank means "translate this from the English
        // above when saving"; a non-blank value is the admin's own wording and
        // is stored as typed. Limits are double the English ones because
        // Arabic output is routinely longer than its source.
        @Size(max = 400)
        String titleAr,

        @Size(max = 40000)
        String descriptionAr,

        @Size(max = 400)
        String locationAr,

        @DecimalMin(value = "0", inclusive = true, message = "Area cannot be negative")
        BigDecimal area,

        @DecimalMin(value = "0", inclusive = true, message = "Price cannot be negative")
        BigDecimal price,

        @Size(max = 500)
        String coverImageUrl,

        List<@Size(max = 500) String> imageUrls,

        Boolean published
) {
}
