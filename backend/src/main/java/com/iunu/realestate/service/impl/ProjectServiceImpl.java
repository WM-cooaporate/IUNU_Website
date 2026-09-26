package com.iunu.realestate.service.impl;

import com.iunu.realestate.dto.request.ProjectRequest;
import com.iunu.realestate.dto.response.ProjectResponse;
import com.iunu.realestate.entity.AuditAction;
import com.iunu.realestate.entity.Project;
import com.iunu.realestate.exception.ResourceNotFoundException;
import com.iunu.realestate.repository.ProjectRepository;
import com.iunu.realestate.service.AuditLogService;
import com.iunu.realestate.service.ImageStorage;
import com.iunu.realestate.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private static final String NOT_FOUND_MESSAGE = "Project not found";

    private final ProjectRepository projectRepository;
    private final ImageStorage imageStorage;
    private final AuditLogService auditLogService;
    private final PlatformTransactionManager transactionManager;

    private static final String AUDIT_TARGET = "PROJECT";

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

        Project saved = projectRepository.save(project);
        auditLogService.record(AuditAction.PROJECT_CREATED, AUDIT_TARGET, saved.getId(),
                "created; published " + saved.isPublished());
        return ProjectResponse.from(saved);
    }

    @Override
    @Transactional
    public ProjectResponse update(Long id, ProjectRequest request) {
        Project project = findOrThrow(id);
        String previousCover = project.getCoverImageUrl();
        boolean wasPublished = project.isPublished();

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
        auditLogService.record(AuditAction.PROJECT_UPDATED, AUDIT_TARGET, id,
                wasPublished == saved.isPublished()
                        ? "updated"
                        : "updated; published " + wasPublished + "\u2192" + saved.isPublished());
        deleteCoverIfOrphaned(previousCover, saved.getCoverImageUrl(), id);
        return ProjectResponse.from(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Project project = findOrThrow(id);
        String cover = project.getCoverImageUrl();
        projectRepository.delete(project);
        auditLogService.record(AuditAction.PROJECT_DELETED, AUDIT_TARGET, id, "deleted");
        deleteCoverIfOrphaned(cover, null, id);
    }

    /**
     * Not @Transactional, on purpose: a transaction held around the file write
     * holds a database connection from a pool of five for all that time, for
     * work the database has no part in. So:
     * <ol>
     *   <li>store the image, no transaction open;</li>
     *   <li>point the row at it in one short transaction;</li>
     *   <li>if that fails, remove the image again - unless something already
     *       uses it, since storage is content-addressed and the same bytes may
     *       be another project's cover.</li>
     * </ol>
     */
    @Override
    public ProjectResponse setCoverImage(Long id, MultipartFile file) {
        // 404 before spending an upload on a project that does not exist.
        if (!projectRepository.existsById(id)) {
            throw new ResourceNotFoundException(NOT_FOUND_MESSAGE);
        }
        String newCover = imageStorage.store(file, ImageStorage.PROJECTS_FOLDER);
        try {
            return new TransactionTemplate(transactionManager).execute(status -> {
                Project project = findOrThrow(id);
                String previousCover = project.getCoverImageUrl();

                project.setCoverImageUrl(newCover);
                Project saved = projectRepository.save(project);
                auditLogService.record(AuditAction.IMAGE_UPLOADED, AUDIT_TARGET, id, "cover image replaced");

                deleteCoverIfOrphaned(previousCover, saved.getCoverImageUrl(), id);
                return ProjectResponse.from(saved);
            });
        } catch (RuntimeException exception) {
            if (!projectRepository.existsByCoverImageUrl(newCover)) {
                imageStorage.deleteIfStored(newCover, ImageStorage.PROJECTS_FOLDER);
            }
            throw exception;
        }
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
        imageStorage.deleteIfStored(previousCover, ImageStorage.PROJECTS_FOLDER);
    }
}
