package com.iunu.realestate.controller;

import com.iunu.realestate.dto.request.TranslationPreviewRequest;
import com.iunu.realestate.dto.response.TranslationBackfillResponse;
import com.iunu.realestate.dto.response.TranslationPreviewResponse;
import com.iunu.realestate.entity.AuditAction;
import com.iunu.realestate.service.AuditLogService;
import com.iunu.realestate.translation.PropertyTranslationBackfillService;
import com.iunu.realestate.translation.TranslationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Translation tools for the dashboard. Sits under /api/admin/**, which
 * SecurityConfig already restricts to ADMIN; @PreAuthorize repeats that at the
 * method, matching how every other admin endpoint in this codebase is guarded.
 *
 * With no API key configured neither endpoint fails: both answer
 * enabled=false, and the dashboard tells the admin why nothing happened.
 */
@Tag(name = "Admin translations")
@RestController
@RequestMapping("/api/admin/translations")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTranslationController {

    private final TranslationService translationService;
    private final PropertyTranslationBackfillService backfillService;
    private final AuditLogService auditLogService;

    /** Fills the Arabic fields of the open form without saving anything. */
    @PostMapping("/preview")
    public ResponseEntity<TranslationPreviewResponse> preview(
            @Valid @RequestBody TranslationPreviewRequest request) {
        if (!translationService.isEnabled()) {
            return ResponseEntity.ok(new TranslationPreviewResponse(null, null, null, false));
        }

        List<String> sources = new ArrayList<>(3);
        sources.add(request.title());
        sources.add(request.description());
        sources.add(request.location());

        List<String> translated = translationService.translateEnToAr(sources);
        return ResponseEntity.ok(new TranslationPreviewResponse(
                valueAt(translated, 0), valueAt(translated, 1), valueAt(translated, 2), true));
    }

    /** Fills the Arabic of existing projects, touching only the fields that are still null. */
    @PostMapping("/properties/backfill")
    public ResponseEntity<TranslationBackfillResponse> backfillProperties() {
        TranslationBackfillResponse result = backfillService.backfill();
        // After the run, not inside it: the backfill is deliberately one short
        // transaction per property, so there is no single transaction to join.
        auditLogService.record(AuditAction.TRANSLATION_BACKFILL_RUN, "PROPERTY", null,
                "scanned " + result.scanned() + ", updated " + result.updated() + ", failed " + result.failed()
                        + (result.enabled() ? "" : "; translation disabled"));
        return ResponseEntity.ok(result);
    }

    private static String valueAt(List<String> values, int index) {
        return (values != null && index < values.size()) ? values.get(index) : null;
    }
}
