package com.iunu.realestate.controller;

import com.iunu.realestate.dto.request.CareerApplicationRequest;
import com.iunu.realestate.service.CareerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Careers")
@RestController
@RequestMapping("/api/careers")
@RequiredArgsConstructor
public class CareerController {

    /** "%PDF-" - the only file signature this endpoint accepts. */
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46, 0x2D};

    private final CareerService careerService;

    @Operation(summary = "Submit a job application, optionally with a PDF CV (max 5MB)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> apply(
            @Valid @ModelAttribute CareerApplicationRequest request,
            @RequestPart(name = "resume", required = false) MultipartFile resume
    ) {
        // Content-Type and filename are both attacker-controlled, so the
        // actual bytes decide: only a real PDF gets attached to the email.
        if (resume != null && !resume.isEmpty() && !isPdf(resume)) {
            return ResponseEntity.unprocessableEntity().build();
        }

        careerService.sendApplication(
                request.getFullName(),
                request.getEmail(),
                request.getPhone(),
                request.getPosition(),
                request.getMessage(),
                resume);

        return ResponseEntity.accepted().build();
    }

    private static boolean isPdf(MultipartFile file) {
        try (var stream = file.getInputStream()) {
            byte[] header = stream.readNBytes(PDF_MAGIC.length);
            return java.util.Arrays.equals(header, PDF_MAGIC);
        } catch (java.io.IOException exception) {
            return false;
        }
    }
}
