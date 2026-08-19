package com.iunu.realestate.controller;

import com.iunu.realestate.dto.request.NewsletterRequest;
import com.iunu.realestate.dto.response.MessageResponse;
import com.iunu.realestate.service.NewsletterService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Newsletter")
@RestController
@RequestMapping("/api/newsletter")
@RequiredArgsConstructor
public class NewsletterController {

    private final NewsletterService newsletterService;

    @PostMapping
    public ResponseEntity<MessageResponse> subscribe(@Valid @RequestBody NewsletterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(newsletterService.subscribe(request));
    }
}
