package com.iunu.realestate.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {
    }

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, List.of());
    }

    public static ApiError ofFieldErrors(int status, String error, String message, String path, Map<String, String> errors) {
        List<FieldError> fieldErrors = errors.entrySet().stream()
                .map(e -> new FieldError(e.getKey(), e.getValue()))
                .toList();
        return new ApiError(Instant.now(), status, error, message, path, fieldErrors);
    }
}
