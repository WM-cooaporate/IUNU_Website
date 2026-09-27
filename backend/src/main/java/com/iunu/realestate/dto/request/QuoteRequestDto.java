package com.iunu.realestate.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record QuoteRequestDto(

        @NotBlank(message = "Name is required")
        @Size(max = 150)
        String name,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[+0-9 ()-]{6,30}$", message = "Phone number is invalid")
        String phone,

        @NotBlank(message = "City is required")
        @Size(max = 100)
        String city,

        @NotBlank(message = "Email is required")
        @Email(message = "Email is invalid")
        @Size(max = 190)
        String email,

        @NotBlank(message = "Project is required")
        @Pattern(regexp = "^(residential|commercial|administrative)$", message = "Invalid project type")
        String project,

        @Pattern(regexp = "^$|^[+0-9 ()-]{6,30}$", message = "WhatsApp number is invalid")
        String whatsapp,

        @NotBlank(message = "Space type is required")
        @Pattern(regexp = "^(apartment|villa|office|commercial)$", message = "Invalid space type")
        String spaceType
) {
}
