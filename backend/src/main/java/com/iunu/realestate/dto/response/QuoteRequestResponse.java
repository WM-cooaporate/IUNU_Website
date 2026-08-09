package com.iunu.realestate.dto.response;

import com.iunu.realestate.entity.QuoteRequest;

import java.time.Instant;

public record QuoteRequestResponse(
        Long id,
        String name,
        String phone,
        String city,
        String email,
        String project,
        String whatsapp,
        String spaceType,
        boolean handled,
        Instant createdAt
) {
    public static QuoteRequestResponse from(QuoteRequest entity) {
        return new QuoteRequestResponse(
                entity.getId(),
                entity.getName(),
                entity.getPhone(),
                entity.getCity(),
                entity.getEmail(),
                entity.getProject(),
                entity.getWhatsapp(),
                entity.getSpaceType(),
                entity.isHandled(),
                entity.getCreatedAt()
        );
    }
}
