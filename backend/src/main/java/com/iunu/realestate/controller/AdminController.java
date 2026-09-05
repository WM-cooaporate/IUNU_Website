package com.iunu.realestate.controller;

import com.iunu.realestate.dto.request.CreateAdminUserRequest;
import com.iunu.realestate.dto.response.ContactMessageResponse;
import com.iunu.realestate.dto.response.QuoteRequestResponse;
import com.iunu.realestate.dto.response.UserResponse;
import com.iunu.realestate.service.AdminUserService;
import com.iunu.realestate.service.ContactService;
import com.iunu.realestate.service.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
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

/**
 * Admin-only views over the leads captured from the public contact and
 * quote-request forms, plus staff account management. Authorization is
 * enforced both at the URL level (SecurityConfig: /api/admin/** ->
 * ROLE_ADMIN) and again here via @PreAuthorize for defense in depth.
 */
@Tag(name = "Admin")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ContactService contactService;
    private final QuoteService quoteService;
    private final AdminUserService adminUserService;

    @Operation(summary = "List staff accounts")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users")
    public ResponseEntity<Page<UserResponse>> listUsers(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminUserService.list(pageable));
    }

    @Operation(summary = "Create another admin. The only way to mint an ADMIN besides the startup bootstrap.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateAdminUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminUserService.create(request));
    }

    @GetMapping("/contacts")
    public ResponseEntity<Page<ContactMessageResponse>> listContacts(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(contactService.listForAdmin(pageable));
    }

    @PatchMapping("/contacts/{id}/handled")
    public ResponseEntity<ContactMessageResponse> markContactHandled(@PathVariable Long id) {
        return ResponseEntity.ok(contactService.markHandled(id));
    }

    @GetMapping("/quotes")
    public ResponseEntity<Page<QuoteRequestResponse>> listQuotes(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(quoteService.listForAdmin(pageable));
    }

    @PatchMapping("/quotes/{id}/handled")
    public ResponseEntity<QuoteRequestResponse> markQuoteHandled(@PathVariable Long id) {
        return ResponseEntity.ok(quoteService.markHandled(id));
    }
}
