package com.iunu.realestate.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email is invalid")
        @Size(max = 190, message = "Email is too long")
        String email
) {
}
