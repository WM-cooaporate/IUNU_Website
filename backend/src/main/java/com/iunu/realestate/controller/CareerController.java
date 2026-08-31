package com.iunu.realestate.controller;

import com.iunu.realestate.service.CareerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Careers")
@RestController
@RequestMapping("/api/careers")
@RequiredArgsConstructor
public class CareerController {

    private final CareerService careerService;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Void> apply(
            @RequestParam String fullName,
            @RequestParam String email,
            @RequestParam String phone,
            @RequestParam String position,
            @RequestParam String message,
            @RequestParam(required = false) MultipartFile resume
    ) {
        if (!StringUtils.hasText(fullName) || !StringUtils.hasText(email)
                || !StringUtils.hasText(phone) || !StringUtils.hasText(position)
                || !StringUtils.hasText(message)) {
            return ResponseEntity.badRequest().build();
        }
        careerService.sendApplication(fullName, email, phone, position, message, resume);
        return ResponseEntity.accepted().build();
    }
}
