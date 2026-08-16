package com.mudassirshahzad.eka.api.exception;

import com.mudassirshahzad.eka.api.security.TooManyLoginAttemptsException;
import com.mudassirshahzad.eka.application.generation.GenerationException;
import com.mudassirshahzad.eka.application.retrieval.InvalidRetrievalRequestException;
import com.mudassirshahzad.eka.application.retrieval.RetrievalException;
import com.mudassirshahzad.eka.application.shared.DuplicateResourceException;
import com.mudassirshahzad.eka.application.shared.InvalidCredentialsException;
import com.mudassirshahzad.eka.application.shared.ResourceNotFoundException;
import com.mudassirshahzad.eka.domain.generation.exception.LlmException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * Centralized exception → RFC 7807 {@link ProblemDetail} mapping (ADR O03). Every handler here
 * surfaces only structural information (status, a generic or ID-based message) — generated text,
 * prompt content, and user query text are never included in a response body or logged, matching
 * the project-wide logging policy.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} as of v0.6.1 (ADR EX02) so Spring MVC's own
 * framework-level exceptions — malformed JSON ({@code HttpMessageNotReadableException}), a
 * non-UUID {@code @PathVariable} ({@code MethodArgumentTypeMismatchException}), an unsupported
 * HTTP method, etc. — resolve to the correct 4xx instead of falling through to
 * {@link #handleUnexpected}'s generic 500. {@code ResponseEntityExceptionHandler.handleException}
 * is {@code final} and already {@code @ExceptionHandler}-mapped to every one of those types, so
 * they are never candidates for {@link #handleUnexpected} in the first place — Spring resolves
 * {@code @ExceptionHandler} methods by the most specific exception-type match across the whole
 * class hierarchy, not by declaring-class proximity, so the inherited, more-specific mappings win
 * automatically. Every one of those exception types has implemented {@code ErrorResponse} since
 * Spring Framework 6, so the base class's default handling already returns a {@link ProblemDetail}
 * body — no per-type handler method needed here to keep the response shape consistent with every
 * other handler in this class.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

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

    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public ProblemDetail handleTooManyLoginAttempts(TooManyLoginAttemptsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
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

    /**
     * Overrides the protected hook {@link ResponseEntityExceptionHandler} exposes for exactly
     * this exception type — not a new {@code @ExceptionHandler(MethodArgumentNotValidException.class)}
     * method — because the base class's {@code handleException} is {@code final} and already owns
     * that mapping; declaring a second, competing {@code @ExceptionHandler} for the same exact type
     * would be an ambiguous mapping. This preserves the exact field-error-joining response body
     * this endpoint returned before v0.6.1 (ADR EX02).
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        return handleExceptionInternal(ex, problemDetail, headers, status, request);
    }

    /**
     * More specific than — and therefore dispatched ahead of — {@link #handleUpstreamFailure}
     * below, even though {@link InvalidRetrievalRequestException} extends {@link RetrievalException}
     * (P05.5, ADR HD04). A too-long or blank query is the caller's mistake, not an upstream
     * failure; before this handler existed it was misreported as a 502.
     */
    @ExceptionHandler(InvalidRetrievalRequestException.class)
    public ProblemDetail handleInvalidRetrievalRequest(InvalidRetrievalRequestException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
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
