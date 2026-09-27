package com.iunu.realestate.dto.response;

import com.iunu.realestate.entity.Project;
import com.iunu.realestate.entity.ProjectStatus;

import java.time.Instant;

/** The exact project shape returned by every public and admin project endpoint. */
public record ProjectResponse(
        Long id,
        String title,
        String description,
        String location,
        ProjectStatus status,
        String priceRange,
        String coverImageUrl,
        boolean published,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getTitle(),
                project.getDescription(),
                project.getLocation(),
                project.getStatus(),
                project.getPriceRange(),
                project.getCoverImageUrl(),
                project.isPublished(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
