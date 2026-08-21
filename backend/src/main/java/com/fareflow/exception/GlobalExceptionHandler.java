package com.fareflow.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates exceptions into RFC 9457 Problem Details.
 *
 * <p>One handler for the whole application means controllers contain no
 * {@code try/catch}, the frontend parses exactly one error shape, and stack
 * traces never leak to clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage());
    }

    @ExceptionHandler({com.fareflow.auth.UnauthenticatedException.class,
            com.fareflow.auth.InvalidCredentialsException.class})
    public ProblemDetail handleUnauthenticated(RuntimeException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "Authentication required", exception.getMessage());
    }

    /** Authenticated but not permitted: 403, never 401. */
    @ExceptionHandler(com.fareflow.auth.ForbiddenException.class)
    public ProblemDetail handleForbidden(com.fareflow.auth.ForbiddenException exception) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", exception.getMessage());
    }

    /**
     * A journey with no computable fare needs explicit acceptance before it can be
     * recorded. The code lets the client prompt rather than guess at the message.
     */
    @ExceptionHandler(com.fareflow.discovery.FareConfirmationRequiredException.class)
    public ProblemDetail handleFareConfirmation(
            com.fareflow.discovery.FareConfirmationRequiredException exception) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT,
                "Fare confirmation required", exception.getMessage());
        problem.setProperty("code",
                com.fareflow.discovery.FareConfirmationRequiredException.CODE);
        problem.setProperty("journey", exception.journeySummary());
        return problem;
    }

    @ExceptionHandler(InvalidStateException.class)
    public ProblemDetail handleInvalidState(InvalidStateException exception) {
        return problem(HttpStatus.CONFLICT, "Conflicting request", exception.getMessage());
    }

    /** Optional upstream AI failures should be retryable, not generic 500s. */
    @ExceptionHandler(com.fareflow.assistant.AssistantUnavailableException.class)
    public ProblemDetail handleAssistantUnavailable(
            com.fareflow.assistant.AssistantUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Ask FareFlow is unavailable",
                exception.getMessage());
    }

    /** Bean Validation failures on {@code @RequestBody} DTOs. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBodyValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid");
        problem.setProperty("errors", fieldErrors);
        return problem;
    }

    /** Validation failures on {@code @RequestParam} / {@code @PathVariable} arguments. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleParameterValidation(HandlerMethodValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more request parameters are invalid");
    }

    /**
     * Raised by AOP-based method validation on {@code @Validated} beans. Not used by
     * the controllers today, but handled so a future {@code @Validated} service
     * cannot turn a validation failure into a 500.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        Map<String, String> violations = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                violations.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more request parameters are invalid");
        problem.setProperty("errors", violations);
        return problem;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParameter(MissingServletRequestParameterException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Missing parameter",
                "Required parameter '%s' is missing".formatted(exception.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid parameter",
                "Parameter '%s' has an invalid value".formatted(exception.getName()));
    }

    /**
     * Domain invariants raised from value objects (for example invalid weights)
     * surface as IllegalArgumentException. These are client errors, not bugs.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage());
    }

    /**
     * Unmatched URLs and wrong HTTP methods are client errors, not server faults.
     * Without these the catch-all below would turn every typo into a 500 and bury
     * a real bug in the logs.
     */
    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.servlet.NoHandlerFoundException.class})
    public ProblemDetail handleNoHandler(Exception exception) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", "No endpoint matches this request");
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException exception) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed",
                "%s is not supported by this endpoint".formatted(exception.getMethod()));
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        // Log the detail, return none of it.
        log.error("Unhandled exception", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
