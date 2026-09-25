package com.mudassirshahzad.eka.api.security;

import com.mudassirshahzad.eka.application.auth.SessionApplicationService;
import io.jsonwebtoken.JwtException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Extracts and validates the {@code Bearer} token on every request, populating the
 * {@code SecurityContext} on success (ADR A05's identity-propagation source).
 *
 * <p>Deliberately never rejects a request itself (ADR A04): a missing header, a malformed
 * header, or a token that fails validation all simply leave the {@code SecurityContext} empty and
 * let the filter chain continue. Spring Security's authorization stage — not this filter —
 * decides whether the target endpoint requires authentication, and {@link RestAuthenticationEntryPoint}
 * is the single place a 401 is ever produced. Short-circuiting here on a bad token would
 * incorrectly reject requests to {@code permitAll} endpoints (login, health, Swagger).
 *
 * <p>Every rejected token increments {@code eka.auth.failures{type=token}} (P05.4, ADR OB02) —
 * the same counter {@code AuthenticateUserUseCase} increments for bad login credentials, so
 * operators can see total authentication-failure volume from one metric name.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX         = "Bearer ";

    private final JwtTokenProvider          jwtTokenProvider;
    private final MeterRegistry             meterRegistry;
    private final SessionApplicationService sessionApplicationService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(AUTHORIZATION_HEADER);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                JwtAuthenticationToken authentication = jwtTokenProvider.parseToken(token);

                // A valid signature is necessary but no longer sufficient (WP-2, ADR RT01). The
                // session behind this token must still be active, which is what lets a leaked
                // access token be killed before it expires on its own. A revoked session is
                // treated exactly like an invalid token — context stays empty, and Spring
                // Security's authorization stage produces the 401 (ADR A04 unchanged).
                if (!sessionApplicationService.isSessionActive(authentication.sessionId())) {
                    log.debug("Rejected bearer token for a revoked or expired session");
                    meterRegistry.counter("eka.auth.failures", "type", "session").increment();
                    SecurityContextHolder.clearContext();
                } else {
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("Rejected invalid bearer token: {}", ex.getClass().getSimpleName());
                meterRegistry.counter("eka.auth.failures", "type", "token").increment();
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
