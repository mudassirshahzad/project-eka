package com.mudassirshahzad.eka.api.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The single source of the correlation ID every downstream component logs against (P05.4, ADR
 * OB03) — generates one (or reuses a caller-supplied {@value #CORRELATION_ID_HEADER}), puts it in
 * MDC so every log line for this request carries it automatically (plain or structured — Spring
 * Boot's structured formats pick up MDC natively, no per-call-site code needed), and echoes it
 * back on the response so a caller can quote it in a support ticket.
 *
 * <p>Registered as the very first filter in {@code SecurityConfig}'s chain — before
 * {@code JwtAuthenticationFilter} and before Spring Security's own filters — so that even a
 * request rejected before authentication (malformed, wrong method, etc.) still logs and responds
 * with a correlation ID. MDC is always cleared in a {@code finally} block: this filter runs on a
 * pooled servlet-container thread that will be reused for an unrelated request, and a leaked MDC
 * entry would silently mislabel that next request's logs.
 *
 * <p>A caller-supplied header is untrusted input flowing straight into every log line for the
 * request — accepted only if it matches {@link #SAFE_ID_PATTERN} (bounded length, no control
 * characters), otherwise a fresh ID is generated instead. Without this, a client could inject
 * newlines to forge log entries (log injection) or supply an unbounded string.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    static final String MDC_KEY               = "correlationId";

    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(CORRELATION_ID_HEADER);
        String correlationId = isSafe(incoming) ? incoming : UUID.randomUUID().toString();

        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        MDC.put(MDC_KEY, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static boolean isSafe(String value) {
        return value != null && SAFE_ID_PATTERN.matcher(value).matches();
    }
}
