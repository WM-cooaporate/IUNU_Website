package com.iunu.realestate.controller;

import com.iunu.realestate.dto.request.PropertyRequest;
import com.iunu.realestate.dto.response.PropertyPublicResponse;
import com.iunu.realestate.dto.response.PropertyResponse;
import com.iunu.realestate.entity.AuditAction;
import com.iunu.realestate.entity.PropertyType;
import com.iunu.realestate.service.AuditLogService;
import com.iunu.realestate.service.PropertyService;
import com.iunu.realestate.service.ImageStorage;
import com.iunu.realestate.translation.PropertyTranslationFiller;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

/**
 * Properties. Every handler lives under {@link #BASE}; the two public reads
 * are also served under {@link #V1} (see there). There is no class-level
 * {@code @RequestMapping} so that the alias applies to those two handlers and
 * nothing else - admin reads and every write exist only under {@link #BASE}.
 */
@Tag(name = "Properties")
@RestController
@RequiredArgsConstructor
public class PropertyController {

    static final String BASE = "/api/properties";

    /**
     * Versioned alias for the public reads only. External clients (the k6
     * smoke test) call /api/v1/properties; the website calls BASE. Both reach
     * the same handler, so both return published rows only, in the public shape.
     */
    static final String V1 = "/api/v1/properties";

    private final PropertyService propertyService;
    private final ImageStorage imageStorage;
    private final PropertyTranslationFiller translationFiller;
    private final AuditLogService auditLogService;

    /**
     * How long a browser, and Cloudflare in front of this origin, may serve a
     * public listing without asking again.
     *
     * <p>Short on purpose. An admin publishing a property expects it on the
     * site now, and the in-process cache is already evicted on write - this
     * window is the one piece of staleness that eviction cannot reach, because
     * the copy lives in someone else's cache. A minute is the trade: it
     * collapses a traffic spike into one origin request per URL per minute,
     * and is short enough that nobody notices it.
     */
    private static final CacheControl PUBLIC_READ_CACHE =
            CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic();

    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = BASE + "/images", consumes = "multipart/form-data")
    public ResponseEntity<List<String>> uploadImages(@RequestParam("files") List<MultipartFile> files) {
        List<String> urls = files.stream().map(imageStorage::store).distinct().toList();
        // Written after every file is stored: a batch with one bad file
        // stores none of the later ones and records nothing.
        auditLogService.record(AuditAction.IMAGE_UPLOADED, "PROPERTY_IMAGE", null,
                urls.size() + " image(s) uploaded");
        return ResponseEntity.ok(urls);
    }

    /**
     * Public: published rows only, in the public shape. There is no
     * authenticated variant - an admin token gets exactly the same answer, and
     * the dashboard reads drafts from /admin below.
     */
    @GetMapping({BASE, V1})
    public ResponseEntity<Page<PropertyPublicResponse>> list(
            @RequestParam(required = false) PropertyType type,
            @PageableDefault(size = 12) Pageable pageable
    ) {
        return ResponseEntity.ok()
                .cacheControl(PUBLIC_READ_CACHE)
                .body(propertyService.listPublished(type, pageable));
    }

    /** Public: 404 for a missing id and for a draft alike, so drafts cannot be probed for. */
    @GetMapping({BASE + "/{id}", V1 + "/{id}"})
    public ResponseEntity<PropertyPublicResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok()
                .cacheControl(PUBLIC_READ_CACHE)
                .body(propertyService.getPublishedById(id));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(BASE + "/admin")
    public ResponseEntity<Page<PropertyResponse>> listAllForAdmin(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(propertyService.listAllForAdmin(pageable));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(BASE + "/admin/{id}")
    public ResponseEntity<PropertyResponse> getOneForAdmin(@PathVariable Long id) {
        return ResponseEntity.ok(propertyService.getByIdForAdmin(id));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(BASE)
    public ResponseEntity<PropertyResponse> create(@Valid @RequestBody PropertyRequest request) {
        // Translation happens here rather than in the service on purpose: the
        // controller is not transactional, so the call to Google finishes
        // before create() takes a database connection.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(propertyService.create(translationFiller.fill(request)));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping(BASE + "/{id}")
    public ResponseEntity<PropertyResponse> update(@PathVariable Long id, @Valid @RequestBody PropertyRequest request) {
        return ResponseEntity.ok(propertyService.update(id, translationFiller.fill(request)));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping(BASE + "/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        propertyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
