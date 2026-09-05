package com.iunu.realestate.dto.request;

import com.iunu.realestate.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for POST /api/admin/users - how additional staff accounts are
 * created. There is no public equivalent: an existing admin's token is the
 * only way to mint another ADMIN.
 */
public record CreateAdminUserRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 150, message = "Full name is too long")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email is invalid")
        @Size(max = 190, message = "Email is too long")
        String email,

        @Pattern(regexp = "^[+0-9 ()-]{6,30}$", message = "Phone number is invalid")
        String phone,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "Password must contain at least one letter and one number"
        )
        String password,

        /** Optional; defaults to ADMIN, which is the point of this endpoint. */
        Role role
) {
}
