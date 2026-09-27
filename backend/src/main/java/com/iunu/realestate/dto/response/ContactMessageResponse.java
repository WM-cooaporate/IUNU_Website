package com.iunu.realestate.dto.response;

import com.iunu.realestate.entity.ContactMessage;

import java.time.Instant;

public record ContactMessageResponse(
        Long id,
        String firstName,
        String lastName,
        String phone,
        String email,
        String message,
        boolean handled,
        Instant createdAt
) {
    public static ContactMessageResponse from(ContactMessage entity) {
        return new ContactMessageResponse(
                entity.getId(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getPhone(),
                entity.getEmail(),
                entity.getMessage(),
                entity.isHandled(),
                entity.getCreatedAt()
        );
    }
}
