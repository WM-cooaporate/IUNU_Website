package com.iunu.realestate.controller;

import com.iunu.realestate.dto.response.ProjectResponse;
import com.iunu.realestate.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, unauthenticated read access to projects. Only published projects
 * are ever visible here; drafts live behind /api/admin/projects.
 */
@Tag(name = "Projects (public)")
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @Operation(summary = "List published projects, newest first")
    @GetMapping
    public ResponseEntity<Page<ProjectResponse>> list(@PageableDefault(size = 12) Pageable pageable) {
        return ResponseEntity.ok(projectService.listPublished(pageable));
    }

    @Operation(summary = "Get a published project; 404 if it is a draft or does not exist")
    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getPublishedById(id));
    }
}
