package com.iunu.realestate.dto.response;

/**
 * Result of a cover-image upload. Carries the whole updated project so the
 * dashboard can refresh the row from one response, alongside the stored URL
 * for convenience when all the caller wants is the new image src.
 */
public record CoverImageResponse(
        String coverImageUrl,
        ProjectResponse project
) {
    public static CoverImageResponse of(ProjectResponse project) {
        return new CoverImageResponse(project.coverImageUrl(), project);
    }
}
