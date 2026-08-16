package com.mudassirshahzad.eka.api.config;

import com.mudassirshahzad.eka.api.observability.CorrelationIdFilter;
import com.mudassirshahzad.eka.api.security.JwtAuthenticationFilter;
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
 * <p><b>Authorization is explicitly out of scope.</b> Every authenticated request is currently
 * permitted the same as every other, regardless of role or tenant — that is P05.3's job. This
 * class answers only "who is making this request," never "are they allowed to."
 *
 * <p>{@link CorrelationIdFilter} runs before every other filter in this chain, including
 * {@link DisableEncodeUrlFilter} (Spring Security's own first filter) — P05.4, ADR OB03 — so that
 * even a request Spring Security itself rejects still logs and responds with a correlation ID.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/login",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private final CorrelationIdFilter          correlationIdFilter;
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
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
