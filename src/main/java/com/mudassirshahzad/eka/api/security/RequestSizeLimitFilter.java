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
 * <p><b>The document upload route is exempt (P06.1, ADR PC01).</b> {@code app.request.max-body-bytes}
 * defaults to 1 MiB, correctly sized for JSON API bodies but far too small for a document upload;
 * multipart size there is already governed by the dedicated, purpose-built
 * {@code spring.servlet.multipart.max-file-size}/{@code max-request-size} properties (Tomcat-level
 * enforcement during multipart parsing). The exemption is scoped to the exact upload path <em>and</em>
 * a {@code multipart/*} content type, deliberately not "any multipart-content-typed request" —
 * content type is caller-supplied and unverified at this point in the chain, so exempting by
 * content type alone would let a request to any other endpoint claim to be multipart and bypass
 * the 1 MiB limit in favor of the much larger 100 MB one. Path-scoping closes that: a request has
 * to actually target the one endpoint that legitimately needs the larger limit.
 *
 * <p>Constructs the {@link ProblemDetail} body directly (rather than throwing into
 * {@code GlobalExceptionHandler}) because a {@code Filter} runs before {@code DispatcherServlet},
 * outside the reach of {@code @ExceptionHandler}-based resolution — this keeps the JSON shape
 * identical to every other error response even though the code path necessarily differs.
 */
@Slf4j
@Component
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private static final String MULTIPART_PREFIX = "multipart/";

    /** The sole multipart-consuming route today — see {@code DocumentController#uploadDocument}. */
    private static final String DOCUMENT_UPLOAD_PATH   = "/api/v1/documents";
    private static final String DOCUMENT_UPLOAD_METHOD = "POST";

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
        if (!isExemptUpload(request)) {
            long contentLength = request.getContentLengthLong();
            if (contentLength > maxBodyBytes) {
                log.warn("Rejected oversized request: path={} contentLength={} maxBodyBytes={}",
                        request.getRequestURI(), contentLength, maxBodyBytes);
                writeRejection(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isExemptUpload(HttpServletRequest request) {
        String contentType = request.getContentType();
        boolean isMultipart = contentType != null && contentType.toLowerCase().startsWith(MULTIPART_PREFIX);
        return isMultipart
                && DOCUMENT_UPLOAD_METHOD.equals(request.getMethod())
                && DOCUMENT_UPLOAD_PATH.equals(request.getRequestURI());
    }

    private void writeRejection(HttpServletResponse response) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
                "Request body exceeds the maximum allowed size of " + maxBodyBytes + " bytes.");
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
