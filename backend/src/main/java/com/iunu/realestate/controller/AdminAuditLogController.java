package com.iunu.realestate.controller;

import com.iunu.realestate.dto.response.AuditLogResponse;
import com.iunu.realestate.entity.AuditAction;
import com.iunu.realestate.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only view of the admin audit trail, for the dashboard's Activity tab.
 * There is deliberately no write or delete endpoint: a trail an admin token
 * can edit is not evidence of what that token did.
 */
@Tag(name = "Admin audit log")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/admin/audit-log")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    @Operation(summary = "Admin actions, newest first (max 50 per page)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<Page<AuditLogResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) AuditAction action
    ) {
        return ResponseEntity.ok(auditLogService.list(action, page, size));
    }
}
