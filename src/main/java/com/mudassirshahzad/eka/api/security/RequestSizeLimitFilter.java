package com.mudassirshahzad.eka.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects a request whose declared {@code Content-Length} exceeds {@code app.request.max-body-bytes}
 * before it ever reaches Spring MVC / Jackson deserialization (v0.6.1, ADR EX06) — closes the
 * "no request size limit" gap from the post-Phase-5 audit. Neither Tomcat's {@code maxPostSize}
 * (form-parameter parsing only) nor any Spring MVC property bounds an arbitrary {@code @RequestBody}
 * JSON payload by default, so this is a small, deliberate filter rather than a missing built-in
 * toggle.
 *
 * <p>Checks {@code Content-Length} only, not a streaming byte-count of chunked-encoding bodies
 * without that header — a deliberate, documented scope limit ("keep it simple," this milestone's
 * own instruction), not an oversight. A legitimate client always sends {@code Content-Length}; a
 * chunked-encoding attacker without it is a smaller, harder-to-execute residual risk than the
 * unbounded-body-size gap this closes.
 *
 * <p>Constructs the {@link ProblemDetail} body directly (rather than throwing into
 * {@code GlobalExceptionHandler}) because a {@code Filter} runs before {@code DispatcherServlet},
 * outside the reach of {@code @ExceptionHandler}-based resolution — this keeps the JSON shape
 * identical to every other error response even though the code path necessarily differs.
 */
@Slf4j
@Component
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBodyBytes;
    private final ObjectMapper objectMapper;

    public RequestSizeLimitFilter(
            @Value("${app.request.max-body-bytes:1048576}") long maxBodyBytes,
            ObjectMapper objectMapper) {
        this.maxBodyBytes = maxBodyBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();

        if (contentLength > maxBodyBytes) {
            log.warn("Rejected oversized request: path={} contentLength={} maxBodyBytes={}",
                    request.getRequestURI(), contentLength, maxBodyBytes);
            writeRejection(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeRejection(HttpServletResponse response) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
                "Request body exceeds the maximum allowed size of " + maxBodyBytes + " bytes.");
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
