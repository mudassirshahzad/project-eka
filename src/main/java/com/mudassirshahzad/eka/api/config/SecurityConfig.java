package com.mudassirshahzad.eka.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Temporary, deliberately permissive security configuration (ADR O05).
 *
 * <p><b>This is not "no security" by accident — it is a specific, ADR-approved decision.</b>
 * {@code spring-boot-starter-security} has been a project dependency since before this milestone
 * with no {@link SecurityFilterChain} bean defined; without one, Spring Boot's default
 * auto-configuration would have silently activated the moment the first {@code @RestController}
 * was added — a generated single-use password logged at startup, HTTP Basic on every endpoint.
 * This bean exists specifically to prevent that surprise, not to implement authentication.
 *
 * <p>Every request is permitted. CSRF protection is disabled because this API is stateless
 * (no session, no cookies) — CSRF exists to protect session-cookie-authenticated browser
 * requests, which this API does not use and P05.2's token-based auth will not use either.
 *
 * <p><b>This class is replaced outright in P05.2</b> (Authentication Foundation) with real JWT
 * validation — not extended, not layered on top of. Until then, every DTO in {@code api.dto}
 * that would normally come from an authenticated principal (tenantId, userId) is instead an
 * explicit request field, exactly because there is no authentication context to derive it from.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
