package com.iunu.realestate.service.impl;

import com.iunu.realestate.dto.request.ProjectRequest;
import com.iunu.realestate.dto.response.ProjectResponse;
import com.iunu.realestate.entity.Project;
import com.iunu.realestate.exception.ResourceNotFoundException;
import com.iunu.realestate.repository.ProjectRepository;
import com.iunu.realestate.service.ImageStorageService;
import com.iunu.realestate.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private static final String NOT_FOUND_MESSAGE = "Project not found";

    private final ProjectRepository projectRepository;
    private final ImageStorageService imageStorageService;

    @Override
    @Transactional(readOnly = true)
    public Page<ProjectResponse> listPublished(Pageable pageable) {
        return projectRepository.findByPublishedTrueOrderByCreatedAtDesc(pageable).map(ProjectResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResponse getPublishedById(Long id) {
        return projectRepository.findByIdAndPublishedTrue(id)
                .map(ProjectResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProjectResponse> listAllForAdmin(Pageable pageable) {
        return projectRepository.findAllByOrderByCreatedAtDesc(pageable).map(ProjectResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResponse getByIdForAdmin(Long id) {
        return ProjectResponse.from(findOrThrow(id));
    }

    @Override
    @Transactional
    public ProjectResponse create(ProjectRequest request) {
        Project project = Project.builder()
                .title(request.title().trim())
                .description(request.description())
                .location(request.location())
                .status(request.status())
                .priceRange(request.priceRange())
                .coverImageUrl(request.coverImageUrl())
                // A new project is a draft unless the caller explicitly publishes it.
                .published(Boolean.TRUE.equals(request.published()))
                .build();

        return ProjectResponse.from(projectRepository.save(project));
    }

    @Override
    @Transactional
    public ProjectResponse update(Long id, ProjectRequest request) {
        Project project = findOrThrow(id);
        String previousCover = project.getCoverImageUrl();

        project.setTitle(request.title().trim());
        project.setDescription(request.description());
        project.setLocation(request.location());
        project.setStatus(request.status());
        project.setPriceRange(request.priceRange());
        project.setCoverImageUrl(request.coverImageUrl());
        // Omitting `published` leaves the current state alone, so an edit
        // never silently unpublishes a live project.
        if (request.published() != null) {
            project.setPublished(request.published());
        }

        Project saved = projectRepository.save(project);
        deleteCoverIfOrphaned(previousCover, saved.getCoverImageUrl(), id);
        return ProjectResponse.from(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Project project = findOrThrow(id);
        String cover = project.getCoverImageUrl();
        projectRepository.delete(project);
        deleteCoverIfOrphaned(cover, null, id);
    }

    @Override
    @Transactional
    public ProjectResponse setCoverImage(Long id, MultipartFile file) {
        Project project = findOrThrow(id);
        String previousCover = project.getCoverImageUrl();

        project.setCoverImageUrl(imageStorageService.store(file, ImageStorageService.PROJECTS_FOLDER));
        Project saved = projectRepository.save(project);

        deleteCoverIfOrphaned(previousCover, saved.getCoverImageUrl(), id);
        return ProjectResponse.from(saved);
    }

    private Project findOrThrow(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
    }

    /**
     * Removes a replaced cover image from storage, but only once nothing
     * references it. Files are content-addressed, so two projects uploading
     * the same image share one file - deleting on replace without this check
     * would break the other project's cover.
     */
    private void deleteCoverIfOrphaned(String previousCover, String currentCover, Long projectId) {
        if (previousCover == null || previousCover.equals(currentCover)) {
            return;
        }
        if (projectRepository.existsByCoverImageUrlAndIdNot(previousCover, projectId)) {
            return;
        }
        imageStorageService.deleteIfStored(previousCover, ImageStorageService.PROJECTS_FOLDER);
    }
}
