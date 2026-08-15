package com.mudassirshahzad.eka.api.exception;

import com.mudassirshahzad.eka.application.generation.GenerationException;
import com.mudassirshahzad.eka.application.retrieval.RetrievalException;
import com.mudassirshahzad.eka.application.shared.DuplicateResourceException;
import com.mudassirshahzad.eka.application.shared.InvalidCredentialsException;
import com.mudassirshahzad.eka.application.shared.ResourceNotFoundException;
import com.mudassirshahzad.eka.domain.generation.exception.LlmException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Centralized exception → RFC 7807 {@link ProblemDetail} mapping (ADR O03). Every handler here
 * surfaces only structural information (status, a generic or ID-based message) — generated text,
 * prompt content, and user query text are never included in a response body or logged, matching
 * the project-wide logging policy.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicate(DuplicateResourceException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /**
     * Catches denials from both {@code AuthorizationInterceptor} (role checks) and any future
     * {@code hasRole(...)}-style Spring Security expression — the message is deliberately generic
     * (never "wrong role" vs. "not your resource") so a caller cannot distinguish the two (ADR AZ03).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "You do not have permission to perform this action.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler({GenerationException.class, RetrievalException.class, LlmException.class})
    public ProblemDetail handleUpstreamFailure(RuntimeException ex) {
        log.error("Upstream RAG pipeline failure: {}", ex.getClass().getSimpleName());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "The request could not be completed due to an upstream failure.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.");
    }
}
