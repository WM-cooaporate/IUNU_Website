package com.iunu.realestate.controller;

import com.iunu.realestate.dto.request.ProjectRequest;
import com.iunu.realestate.dto.response.CoverImageResponse;
import com.iunu.realestate.dto.response.ProjectResponse;
import com.iunu.realestate.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin management of projects, including drafts.
 *
 * Authorization is enforced twice on purpose: once by URL in SecurityConfig
 * (/api/admin/** -> ROLE_ADMIN) and again by @PreAuthorize here, so a future
 * edit to the URL rules cannot silently expose these endpoints.
 */
@Tag(name = "Projects (admin)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/admin/projects")
@RequiredArgsConstructor
public class AdminProjectController {

    private final ProjectService projectService;

    @Operation(summary = "List all projects (published and draft), newest first")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<Page<ProjectResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(projectService.listAllForAdmin(pageable));
    }

    @Operation(summary = "Get any project by id, published or not")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getByIdForAdmin(id));
    }

    @Operation(summary = "Create a project; starts as a draft unless published=true is sent")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.create(request));
    }

    @Operation(summary = "Update a project, including toggling published")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponse> update(@PathVariable Long id, @Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.ok(projectService.update(id, request));
    }

    @Operation(summary = "Delete a project")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Upload/replace the cover image (JPG, PNG or WEBP, max 5MB)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/{id}/cover-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CoverImageResponse> uploadCoverImage(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(CoverImageResponse.of(projectService.setCoverImage(id, file)));
    }
}
