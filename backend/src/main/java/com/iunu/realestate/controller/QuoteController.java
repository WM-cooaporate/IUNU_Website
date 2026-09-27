package com.iunu.realestate.controller;

import com.iunu.realestate.dto.request.QuoteRequestDto;
import com.iunu.realestate.dto.response.MessageResponse;
import com.iunu.realestate.service.QuoteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Quotes")
@RestController
@RequestMapping("/api/quotes")
@RequiredArgsConstructor
public class QuoteController {

    private final QuoteService quoteService;

    @PostMapping
    public ResponseEntity<MessageResponse> submit(@Valid @RequestBody QuoteRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(quoteService.submit(request));
    }
}
