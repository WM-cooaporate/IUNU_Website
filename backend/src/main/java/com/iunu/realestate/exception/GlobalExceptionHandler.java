package com.iunu.realestate.exception;

import com.iunu.realestate.dto.response.ApiError;
import com.iunu.realestate.security.events.SecurityEventType;
import com.iunu.realestate.security.events.SecurityEvents;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Central mapping from exceptions to JSON error responses. Keeps stack
 * traces and internal details out of API responses (only logged server-side)
 * so the API cannot be used to fingerprint the implementation.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final SecurityEvents securityEvents;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.put(fe.getField(), fe.getDefaultMessage()));

        ApiError body = ApiError.ofFieldErrors(
                HttpStatus.BAD_REQUEST.value(), "Bad Request", "Validation failed", request.getRequestURI(), fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * A required @RequestParam / form field was omitted. Without this the
     * catch-all below would turn a caller's mistake into a 500 and log a
     * stack trace at ERROR for it.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        ApiError body = ApiError.ofFieldErrors(
                HttpStatus.BAD_REQUEST.value(), "Bad Request", "Validation failed", request.getRequestURI(),
                Map.of(ex.getParameterName(), "This field is required"));
        return ResponseEntity.badRequest().body(body);
    }

    /** Constraint violations from @Validated method parameters (not request bodies). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                fieldErrors.put(lastPathNode(violation.getPropertyPath().toString()), violation.getMessage()));

        ApiError body = ApiError.ofFieldErrors(
                HttpStatus.BAD_REQUEST.value(), "Bad Request", "Validation failed", request.getRequestURI(), fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler({UnauthorizedException.class, BadCredentialsException.class})
    public ResponseEntity<ApiError> handleUnauthorized(RuntimeException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler({ResourceNotFoundException.class, NoSuchElementException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage(), request.getRequestURI()));
    }

    /**
     * A URL nothing is mapped to. Without this the catch-all below turns every
     * probe for a path that does not exist into a 500 plus an ERROR-level stack
     * trace - so a scanner walking a wordlist both gets told "something went
     * wrong here" (which reads as "keep looking") and fills the logs on the way
     * through. It is a 404.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiError> handleUnmappedPath(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(HttpStatus.NOT_FOUND.value(), "Not Found",
                        "The requested resource was not found", request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(HttpStatus.FORBIDDEN.value(), "Forbidden",
                        "You do not have permission to access this resource", request.getRequestURI()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage(), request.getRequestURI()));
    }

    /**
     * An unknown or non-sortable ?sort= property. Spring Data raises this from
     * deep inside query creation, so without a mapping it reaches the catch-all
     * and becomes a 500 - which both looks like a server fault and tells the
     * caller, via the logged stack trace and the error shape, that they found
     * something. It is a bad parameter: 400.
     *
     * <p>The message is deliberately generic. Echoing the rejected property
     * back turns this endpoint into a field oracle: try ?sort=password, see
     * whether the error changes, and learn the entity's shape one guess at a
     * time.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiError> handleUnknownSortProperty(
            PropertyReferenceException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), "Bad Request",
                        "Unsupported sort or filter parameter", request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT.value(), "Conflict",
                        "The request could not be completed due to a data conflict", request.getRequestURI()));
    }

    /**
     * Rejected uploads (empty file, disallowed content type, bad storage
     * folder) surface as IllegalArgumentException from the ImageStorage provider.
     * These are caller mistakes, so they get a 400 with the specific reason
     * rather than falling through to a generic 500.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        securityEvents.record(SecurityEventType.UPLOAD_REJECTED, null, null, request, Map.of("reason", "too_large"));
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiError.of(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Payload Too Large",
                        "The uploaded file is too large", request.getRequestURI()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingPart(MissingServletRequestPartException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), "Bad Request",
                        "Missing required file part '" + ex.getRequestPartName() + "'", request.getRequestURI()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class,
            HttpRequestMethodNotSupportedException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleMalformedRequest(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", "The request could not be processed", request.getRequestURI()));
    }

    /** "apply.fullName" -> "fullName", so clients see the field they sent. */
    private static String lastPathNode(String propertyPath) {
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot < 0 ? propertyPath : propertyPath.substring(lastDot + 1);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return ResponseEntity.internalServerError()
                .body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error",
                        "An unexpected error occurred", request.getRequestURI()));
    }
}
