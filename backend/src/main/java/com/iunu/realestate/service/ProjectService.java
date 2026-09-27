package com.iunu.realestate.service;

import com.iunu.realestate.dto.request.ProjectRequest;
import com.iunu.realestate.dto.response.ProjectResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface ProjectService {

    /** Published projects only, newest first. Backs the public site. */
    Page<ProjectResponse> listPublished(Pageable pageable);

    /** Throws ResourceNotFoundException for both missing and unpublished projects. */
    ProjectResponse getPublishedById(Long id);

    Page<ProjectResponse> listAllForAdmin(Pageable pageable);

    ProjectResponse getByIdForAdmin(Long id);

    ProjectResponse create(ProjectRequest request);

    ProjectResponse update(Long id, ProjectRequest request);

    void delete(Long id);

    ProjectResponse setCoverImage(Long id, MultipartFile file);
}
