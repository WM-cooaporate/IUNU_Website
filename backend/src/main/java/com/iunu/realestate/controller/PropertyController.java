package com.iunu.realestate.controller;

import com.iunu.realestate.dto.request.PropertyRequest;
import com.iunu.realestate.dto.response.PropertyResponse;
import com.iunu.realestate.entity.PropertyType;
import com.iunu.realestate.service.PropertyService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Properties")
@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;

    @GetMapping
    public ResponseEntity<Page<PropertyResponse>> list(
            @RequestParam(required = false) PropertyType type,
            @PageableDefault(size = 12) Pageable pageable
    ) {
        return ResponseEntity.ok(propertyService.listPublished(type, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PropertyResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(propertyService.getPublishedById(id));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public ResponseEntity<Page<PropertyResponse>> listAllForAdmin(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(propertyService.listAllForAdmin(pageable));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/{id}")
    public ResponseEntity<PropertyResponse> getOneForAdmin(@PathVariable Long id) {
        return ResponseEntity.ok(propertyService.getByIdForAdmin(id));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<PropertyResponse> create(@Valid @RequestBody PropertyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(propertyService.create(request));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<PropertyResponse> update(@PathVariable Long id, @Valid @RequestBody PropertyRequest request) {
        return ResponseEntity.ok(propertyService.update(id, request));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        propertyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
