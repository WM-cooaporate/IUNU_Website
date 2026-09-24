package com.iunu.realestate.exception;

import com.iunu.realestate.security.RequestIdFilter;
import com.iunu.realestate.security.events.SecurityEventType;
import com.iunu.realestate.security.events.SecurityEvents;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Central mapping from exceptions to RFC 7807 {@link ProblemDetail} responses.
 *
 * <p>Every message a caller sees is either written here or written by this
 * application for a caller to read (a {@link BadRequestException}, a
 * constraint annotation's {@code message}). Nothing from a framework or
 * library exception reaches a response: those messages carry class names,
 * parser positions, SQL and file paths, and an API that echoes them is a free
 * map of its implementation. The cause is logged server-side instead.
 *
 * <p>Alongside the standard fields, each body keeps the fields the API's
 * earlier error shape had ({@code error}, {@code message}, {@code path},
 * {@code timestamp}, {@code fieldErrors}), which the dashboard reads and
 * which the servlet filters still write, so every error has one shape.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    public static final String MALFORMED_BODY = "Malformed request body";
    public static final String NOT_FOUND = "The requested resource was not found";
    public static final String UNEXPECTED = "Unexpected error";

    private final SecurityEvents securityEvents;

    // --- 400 -----------------------------------------------------------------

    /**
     * {@code @Valid} failed on a body or form. Only constraint-annotation
     * messages are returned: a binding failure (a string where a number goes,
     * on a form) carries Spring's own "Failed to convert ... java.lang..."
     * message, so it gets a fixed one instead.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(),
                    fieldError.isBindingFailure() ? "Invalid value" : fieldError.getDefaultMessage());
        }
        return validationProblem(fieldErrors, request);
    }

    /** Constraint violations from @Validated method parameters (not request bodies). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                fieldErrors.putIfAbsent(lastPathNode(violation.getPropertyPath().toString()), violation.getMessage()));
        return validationProblem(fieldErrors, request);
    }

    /** Spring MVC's built-in method validation of constrained @RequestParam / @PathVariable arguments. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleMethodValidation(
            HandlerMethodValidationException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getAllValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            String message = result.getResolvableErrors().stream()
                    .map(MessageSourceResolvable::getDefaultMessage)
                    .findFirst().orElse("Invalid value");
            fieldErrors.putIfAbsent(name == null ? "parameter" : name, message);
        });
        return validationProblem(fieldErrors, request);
    }

    /**
     * Unparseable JSON, a value of the wrong type, an unknown enum constant, a
     * number too large for its field, invalid UTF-8. The parser's message says
     * exactly which, with the Java type it was aiming for - so none of it is
     * returned.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, MALFORMED_BODY, request);
    }

    /** "?page=abc", "/api/projects/abc", "?type=NOPE". */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return validationProblem(Map.of(ex.getName(), "Invalid value"), request);
    }

    /**
     * A required @RequestParam / form field was omitted. Without this the
     * catch-all below would turn a caller's mistake into a 500 and log a
     * stack trace at ERROR for it.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return validationProblem(Map.of(ex.getParameterName(), "This field is required"), request);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ProblemDetail> handleMissingPart(MissingServletRequestPartException ex, HttpServletRequest request) {
        return validationProblem(Map.of(ex.getRequestPartName(), "This file is required"), request);
    }

    /** Missing path variable, header or cookie. */
    @ExceptionHandler({MissingPathVariableException.class, ServletRequestBindingException.class})
    public ResponseEntity<ProblemDetail> handleBinding(ServletRequestBindingException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "The request is missing a required value", request);
    }

    /**
     * A multipart body Tomcat cannot parse - no boundary, truncated, a part
     * with no headers. It is the caller's body that is broken, not the server.
     * (Too large is {@link MaxUploadSizeExceededException}, a subclass, below.)
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ProblemDetail> handleBadMultipart(MultipartException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed multipart request", request);
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
    public ResponseEntity<ProblemDetail> handleUnknownSortProperty(
            PropertyReferenceException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Unsupported sort or filter parameter", request);
    }

    /**
     * Thrown by this application for a bad storage folder or filename, but
     * also by libraries for their own reasons ("No enum constant
     * com.iunu...", "Page index must not be less than zero"), so the message
     * is never passed through.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.debug("Rejected argument on {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", request);
    }

    /** Messages written by this application for the caller, e.g. ImageValidator's. */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    // --- 401 / 403 -------------------------------------------------------------

    /** This application's own "wrong email or password" and "token expired" answers. */
    @ExceptionHandler({UnauthorizedException.class, BadCredentialsException.class})
    public ResponseEntity<ProblemDetail> handleUnauthorized(RuntimeException ex, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    /**
     * Spring Security's to answer, not this class's. A @PreAuthorize refusal
     * raised inside a controller is rethrown so ExceptionTranslationFilter
     * sends it to AuthEntryPointJwt (401 for an anonymous caller) or
     * AccessDeniedHandlerImpl (403) - the same handlers, bodies and security
     * events as a refusal by the URL rules. Declared so that the catch-all
     * below cannot turn either into a 500.
     */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void rethrowSecurityException(RuntimeException ex) {
        throw ex;
    }

    // --- 404 -----------------------------------------------------------------

    /** This application's own not-found, whose messages are written for the caller. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    /**
     * A URL nothing is mapped to, or a lookup that found nothing. Without
     * this the catch-all below turns every probe for a path that does not
     * exist into a 500 plus an ERROR-level stack trace - so a scanner walking
     * a wordlist both gets told "something went wrong here" (which reads as
     * "keep looking") and fills the logs on the way through. It is a 404.
     * JPA's EntityNotFoundException names the entity class, so no message is
     * passed through.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class,
            EntityNotFoundException.class, NoSuchElementException.class})
    public ResponseEntity<ProblemDetail> handleUnmappedPath(Exception ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, NOT_FOUND, request);
    }

    // --- 405 / 406 / 415 -----------------------------------------------------

    /** Carries the Allow header, so the caller learns which methods the route does take. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed", request, ex.getHeaders());
    }

    /**
     * Asked for a representation (Accept) this API does not produce. The
     * body is still written, as application/problem+json - Spring falls back
     * to it for a ProblemDetail when nothing the caller accepts fits.
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ProblemDetail> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_ACCEPTABLE, "Not acceptable", request);
    }

    /** Sent a Content-Type this endpoint does not read. Carries the Accept header listing what it does. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", request, ex.getHeaders());
    }

    // --- 409 / 413 -----------------------------------------------------------

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(ConflictException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /** A unique key or a column limit. The database's message names tables and columns, so it is only logged. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "The request could not be completed due to a data conflict", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleUploadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        securityEvents.record(SecurityEventType.UPLOAD_REJECTED, null, null, request, Map.of("reason", "too_large"));
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "The uploaded file is too large", request);
    }

    // --- 5xx -----------------------------------------------------------------

    /**
     * Cloudinary refused or timed out. The cause was already logged, redacted,
     * where it happened; the admin gets a message that says "try again" rather
     * than a 500 that says "we are broken".
     */
    @ExceptionHandler(ImageServiceUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleImageServiceUnavailable(
            ImageServiceUnavailableException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_GATEWAY, ex.getMessage(), request);
    }

    /**
     * Any other exception Spring itself has already given a status to
     * (ResponseStatusException, an async timeout). The status is kept; its
     * reason text is not.
     */
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ProblemDetail> handleErrorResponse(ErrorResponseException ex, HttpServletRequest request) {
        HttpStatusCode status = ex.getStatusCode();
        if (status.is5xxServerError()) {
            return unexpected(ex, request);
        }
        HttpStatus known = HttpStatus.resolve(status.value());
        return problem(known == null ? HttpStatus.BAD_REQUEST : known,
                known == null ? "Bad request" : known.getReasonPhrase(), request, ex.getHeaders());
    }

    /**
     * Everything unplanned. The caller gets a correlation id and nothing else;
     * the full stack trace is logged under the same id. The id is this
     * request's X-Request-Id (RequestIdFilter), so it also finds every other
     * log line the request wrote.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        return unexpected(ex, request);
    }

    private ResponseEntity<ProblemDetail> unexpected(Exception ex, HttpServletRequest request) {
        String correlationId = MDC.get(RequestIdFilter.MDC_KEY);
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        log.error("Unhandled exception on {} [correlationId={}]", request.getRequestURI(), correlationId, ex);
        ResponseEntity<ProblemDetail> response = problem(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED, request);
        response.getBody().setProperty("correlationId", correlationId);
        return response;
    }

    // --- building the body ---------------------------------------------------

    private static ResponseEntity<ProblemDetail> validationProblem(Map<String, String> fieldErrors, HttpServletRequest request) {
        ResponseEntity<ProblemDetail> response = problem(HttpStatus.BAD_REQUEST, "Validation failed", request);
        response.getBody().setProperty("fieldErrors", fieldErrors.entrySet().stream()
                .map(entry -> Map.of("field", entry.getKey(), "message", entry.getValue()))
                .toList());
        return response;
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail, HttpServletRequest request) {
        return problem(status, detail, request, HttpHeaders.EMPTY);
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String detail, HttpServletRequest request, HttpHeaders headers) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(status.getReasonPhrase());
        String path = request.getRequestURI();
        try {
            body.setInstance(URI.create(path));
        } catch (IllegalArgumentException ignored) {
            // A path Tomcat accepted but java.net.URI does not; "path" below still has it.
        }
        body.setProperty("timestamp", Instant.now());
        body.setProperty("error", status.getReasonPhrase());
        body.setProperty("message", detail);
        body.setProperty("path", path);
        body.setProperty("fieldErrors", List.of());
        return ResponseEntity.status(status).headers(headers).body(body);
    }

    /** "apply.fullName" -> "fullName", so clients see the field they sent. */
    private static String lastPathNode(String propertyPath) {
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot < 0 ? propertyPath : propertyPath.substring(lastDot + 1);
    }
}
