package com.mudassirshahzad.eka.api.controller;

import com.mudassirshahzad.eka.api.config.SecurityConfig;
import com.mudassirshahzad.eka.api.observability.CorrelationIdFilter;
import com.mudassirshahzad.eka.api.security.JwtAuthenticationFilter;
import com.mudassirshahzad.eka.api.security.JwtProperties;
import com.mudassirshahzad.eka.api.security.JwtTokenProvider;
import com.mudassirshahzad.eka.api.security.LoginRateLimiter;
import com.mudassirshahzad.eka.api.security.RequestSizeLimitFilter;
import com.mudassirshahzad.eka.api.security.RestAuthenticationEntryPoint;
import com.mudassirshahzad.eka.api.security.TooManyLoginAttemptsException;
import com.mudassirshahzad.eka.application.shared.InvalidCredentialsException;
import com.mudassirshahzad.eka.application.user.AuthenticateUserUseCase;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.User;
import com.mudassirshahzad.eka.domain.user.UserRole;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.EnumSet;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} slice covering {@link AuthController} against the real
 * {@link SecurityConfig} filter chain — proves {@code POST /api/v1/auth/login} is genuinely
 * {@code permitAll} (no Authorization header attached to any request here) and that failed
 * login attempts surface as 401 via {@link com.mudassirshahzad.eka.api.exception.GlobalExceptionHandler}.
 * A real {@link SimpleMeterRegistry} (P05.4) is provided since {@code JwtAuthenticationFilter} now
 * records a failure counter and a mocked registry would return {@code null} from {@code counter(...)}.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        CorrelationIdFilter.class, RequestSizeLimitFilter.class,
        AuthControllerTest.MeterRegistryTestConfig.class})
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AuthenticateUserUseCase authenticateUserUseCase;
    @MockitoBean private JwtTokenProvider         jwtTokenProvider;
    @MockitoBean private JwtProperties            jwtProperties;
    @MockitoBean private LoginRateLimiter         loginRateLimiter;

    @TestConfiguration
    static class MeterRegistryTestConfig {
        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private final UUID tenantId = UUID.randomUUID();

    @Test
    void login_validCredentials_returnsAccessToken() throws Exception {
        User user = User.create(TenantId.of(tenantId), "user@example.com", "hashed", EnumSet.of(UserRole.USER));
        when(authenticateUserUseCase.execute(any())).thenReturn(user);
        when(jwtTokenProvider.generateAccessToken(any(), any(), any())).thenReturn("signed.jwt.token");
        when(jwtProperties.accessTokenExpiryMs()).thenReturn(900_000L);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","email":"user@example.com","password":"secret"}
                                """.formatted(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("signed.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInMs").value(900_000));
    }

    @Test
    void login_invalidCredentials_returnsUnauthorized() throws Exception {
        when(authenticateUserUseCase.execute(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","email":"user@example.com","password":"wrong"}
                                """.formatted(tenantId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void login_blankEmail_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","email":"","password":"secret"}
                                """.formatted(tenantId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_missingTenantId_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"secret"}
                                """))
                .andExpect(status().isBadRequest());
    }

    /** v0.6.1, ADR EX05: rate limiting runs before credential checks — a limited caller never
     *  reaches {@link AuthenticateUserUseCase}. */
    @Test
    void login_rateLimitExceeded_returnsTooManyRequests() throws Exception {
        doThrow(new TooManyLoginAttemptsException("Too many login attempts. Try again later."))
                .when(loginRateLimiter).checkAllowed(any());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","email":"user@example.com","password":"secret"}
                                """.formatted(tenantId)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
    }
}
