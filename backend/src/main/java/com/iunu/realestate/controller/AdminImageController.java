package com.iunu.realestate.controller;

import com.iunu.realestate.dto.response.ImageMigrationResponse;
import com.iunu.realestate.dto.response.ImageSweepResponse;
import com.iunu.realestate.entity.AuditAction;
import com.iunu.realestate.service.AuditLogService;
import com.iunu.realestate.service.image.ImageMigrationService;
import com.iunu.realestate.service.image.OrphanImageSweeper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Image maintenance for the dashboard. Under /api/admin/**, which
 * SecurityConfig restricts to ADMIN; @PreAuthorize repeats it at the method,
 * as on every other admin endpoint.
 *
 * <p>With the local storage provider both endpoints answer enabled=false and
 * do nothing.
 */
@Tag(name = "Admin images")
@RestController
@RequestMapping("/api/admin/images")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminImageController {

    private final ImageMigrationService migrationService;
    private final OrphanImageSweeper sweeper;
    private final AuditLogService auditLogService;

    @Operation(summary = "Copy legacy /uploads/ images to Cloudinary and rewrite the rows; lists the ones already lost")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/migrate-to-cloud")
    public ResponseEntity<ImageMigrationResponse> migrateToCloud() {
        ImageMigrationResponse result = migrationService.migrate();
        if (result.enabled()) {
            auditLogService.record(AuditAction.IMAGES_MIGRATED, "IMAGE", null,
                    "migrated " + result.migrated() + ", missing " + result.missing().size());
        }
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Find (and unless dryRun, delete) uploaded images nothing references, older than 24h")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/sweep")
    public ResponseEntity<ImageSweepResponse> sweep(@RequestParam(defaultValue = "true") boolean dryRun) {
        ImageSweepResponse result = sweeper.sweep(dryRun);
        if (result.enabled() && !dryRun) {
            auditLogService.record(AuditAction.IMAGES_SWEPT, "IMAGE", null,
                    "scanned " + result.scanned() + ", orphans " + result.orphans() + ", deleted " + result.deleted());
        }
        return ResponseEntity.ok(result);
    }
}
