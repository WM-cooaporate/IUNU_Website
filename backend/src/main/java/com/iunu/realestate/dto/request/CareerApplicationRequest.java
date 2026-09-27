package com.iunu.realestate.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Multipart payload for POST /api/careers.
 *
 * A mutable class rather than a record because the fields arrive as
 * multipart form parts and are bound by @ModelAttribute (setter binding).
 * The file itself stays a separate @RequestPart - Bean Validation has
 * nothing useful to say about bytes.
 */
@Getter
@Setter
public class CareerApplicationRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 150, message = "Full name is too long")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email is invalid")
    @Size(max = 190, message = "Email is too long")
    private String email;

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^[+0-9 ()-]{6,30}$", message = "Phone number is invalid")
    private String phone;

    @NotBlank(message = "Position is required")
    @Size(max = 150, message = "Position is too long")
    private String position;

    @NotBlank(message = "Message is required")
    @Size(max = 5000, message = "Message is too long")
    private String message;
}
