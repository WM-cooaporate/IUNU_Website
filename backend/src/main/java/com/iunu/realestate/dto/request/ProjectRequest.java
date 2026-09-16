package com.iunu.realestate.dto.request;

import com.iunu.realestate.entity.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload for a project.
 *
 * Only {@code title} is required. {@code published} is a Boolean (not a
 * primitive) so that an omitted field is distinguishable from an explicit
 * {@code false}: on create, omitting it means "draft"; on update, omitting
 * it leaves the current publish state untouched.
 */
public record ProjectRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title is too long")
        String title,

        @Size(max = 20000, message = "Description is too long")
        String description,

        @Size(max = 200, message = "Location is too long")
        String location,

        ProjectStatus status,

        @Size(max = 100, message = "Price range is too long")
        String priceRange,

        @Size(max = 500, message = "Cover image URL is too long")
        String coverImageUrl,

        Boolean published
) {
}
