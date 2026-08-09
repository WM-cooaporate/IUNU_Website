package com.iunu.realestate.controller;

import com.iunu.realestate.dto.response.ContactMessageResponse;
import com.iunu.realestate.dto.response.QuoteRequestResponse;
import com.iunu.realestate.service.ContactService;
import com.iunu.realestate.service.QuoteService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only views over the leads captured from the public contact and
 * quote-request forms. Authorization is enforced both at the URL level
 * (SecurityConfig: /api/admin/** -> ROLE_ADMIN) and again here via
 * @PreAuthorize for defense in depth.
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
