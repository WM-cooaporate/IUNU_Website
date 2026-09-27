package com.iunu.realestate.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record NewsletterRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email is invalid")
        String email
) {
}
