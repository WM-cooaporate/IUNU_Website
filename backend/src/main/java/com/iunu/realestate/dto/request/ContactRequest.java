package com.iunu.realestate.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ContactRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 100)
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100)
        String lastName,

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^[+0-9 ()-]{6,30}$", message = "Phone number is invalid")
        String phone,

        @NotBlank(message = "Email is required")
        @Email(message = "Email is invalid")
        @Size(max = 190)
        String email,

        @NotBlank(message = "Message is required")
        @Size(max = 5000, message = "Message is too long")
        String message
) {
}
