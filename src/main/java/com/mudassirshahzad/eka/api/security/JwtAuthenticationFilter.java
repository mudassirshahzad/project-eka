package com.mudassirshahzad.eka.api.security;

import io.jsonwebtoken.JwtException;
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX         = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(AUTHORIZATION_HEADER);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                SecurityContextHolder.getContext().setAuthentication(jwtTokenProvider.parseToken(token));
            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("Rejected invalid bearer token: {}", ex.getClass().getSimpleName());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
