package com.iunu.realestate.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email is invalid")
        @Size(max = 190, message = "Email is too long")
        String email,

        // Generous, so no password that was ever accepted is refused (the
        // bootstrap ADMIN_PASSWORD has no upper limit). It only has to stop a
        // megabyte of "password" reaching the hasher.
        @NotBlank(message = "Password is required")
        @Size(max = 1000, message = "Password is too long")
        String password
) {
}
