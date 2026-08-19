package com.mudassirshahzad.eka.api.config;

import com.mudassirshahzad.eka.api.observability.CorrelationIdFilter;
import com.mudassirshahzad.eka.api.security.JwtAuthenticationFilter;
import com.mudassirshahzad.eka.api.security.RequestSizeLimitFilter;
import com.mudassirshahzad.eka.api.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.session.DisableEncodeUrlFilter;

/**
 * Real JWT-based security configuration (P05.2). Replaces the P05.1 permissive seam outright, not
 * layered on top of it, exactly as ADR O05 committed to.
 *
 * <p>Stateless: no session, no cookies, CSRF disabled — this API never relies on a session cookie
 * for authentication, so CSRF protection (which exists to protect cookie-authenticated browser
 * requests) has nothing to protect here.
 *
 * <p>This class answers only "who is making this request" (authentication). Role and
 * tenant/ownership <em>authorization</em> — "are they allowed to" — is handled elsewhere:
 * {@link com.mudassirshahzad.eka.api.security.AuthorizationInterceptor}, a separate
 * {@code HandlerInterceptor} registered by {@code WebMvcConfig} (P05.3, ADR AZ01), not part of
 * this {@code SecurityFilterChain}. (v0.6.1, ADR EX07: this note previously said authorization was
 * "explicitly out of scope" project-wide and was never updated when P05.3 shipped it — corrected
 * here since a reader of this class alone would otherwise be told something false about the
 * running system.)
 *
 * <p>{@link CorrelationIdFilter} runs before every other filter in this chain, including
 * {@link DisableEncodeUrlFilter} (Spring Security's own first filter) — P05.4, ADR OB03 — so that
 * even a request Spring Security itself rejects still logs and responds with a correlation ID.
 * {@link RequestSizeLimitFilter} runs immediately after it (v0.6.1, ADR EX06) — early enough to
 * reject an oversized body before JWT parsing or Spring MVC ever see it, late enough that the
 * rejection response still carries a correlation ID.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/login",
            "/api/v1/admin/bootstrap",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private final CorrelationIdFilter          correlationIdFilter;
    private final RequestSizeLimitFilter       requestSizeLimitFilter;
    private final JwtAuthenticationFilter      jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthenticationEntryPoint))
                .addFilterBefore(correlationIdFilter, DisableEncodeUrlFilter.class)
                .addFilterAfter(requestSizeLimitFilter, CorrelationIdFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
