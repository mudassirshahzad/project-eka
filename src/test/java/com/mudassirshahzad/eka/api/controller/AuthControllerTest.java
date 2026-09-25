package com.mudassirshahzad.eka.api.controller;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.api.security.JwtAuthenticationToken;
import com.mudassirshahzad.eka.application.auth.SessionApplicationService;
import com.mudassirshahzad.eka.application.auth.IssuedSession;
import com.mudassirshahzad.eka.domain.auth.SessionId;
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

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
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
    @MockitoBean private SessionApplicationService sessionApplicationService;

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
        when(sessionApplicationService.issue(any())).thenReturn(new IssuedSession(
                SessionId.generate(), "raw-refresh-token", user.getId(), user.getTenantId(),
                user.getRoles(), 604_800_000L));
        when(jwtTokenProvider.generateAccessToken(any(), any(), any(), any())).thenReturn("signed.jwt.token");
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

    @Test
    void login_alsoReturnsARefreshToken() throws Exception {
        User user = User.create(TenantId.of(tenantId), "user@example.com", "hashed", EnumSet.of(UserRole.USER));
        when(authenticateUserUseCase.execute(any())).thenReturn(user);
        when(sessionApplicationService.issue(any())).thenReturn(new IssuedSession(
                SessionId.generate(), "the-refresh-secret", user.getId(), user.getTenantId(),
                user.getRoles(), 604_800_000L));
        when(jwtTokenProvider.generateAccessToken(any(), any(), any(), any())).thenReturn("signed.jwt.token");
        when(jwtProperties.accessTokenExpiryMs()).thenReturn(900_000L);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","email":"user@example.com","password":"secret"}
                                """.formatted(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").value("the-refresh-secret"))
                .andExpect(jsonPath("$.refreshExpiresInMs").value(604_800_000L));
    }

    @Test
    void refresh_validToken_returnsANewPair_andIsPubliclyReachable() throws Exception {
        UUID userUuid = UUID.randomUUID();
        when(sessionApplicationService.rotate("old-secret")).thenReturn(new IssuedSession(
                SessionId.generate(), "new-secret", UserId.of(userUuid), TenantId.of(tenantId),
                EnumSet.of(UserRole.USER), 604_800_000L));
        when(jwtTokenProvider.generateAccessToken(any(), any(), any(), any())).thenReturn("new.jwt.token");
        when(jwtProperties.accessTokenExpiryMs()).thenReturn(900_000L);

        // No Authorization header is attached anywhere in this test class, which is what proves
        // /refresh is genuinely permitAll against the real SecurityConfig chain.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"old-secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new.jwt.token"))
                .andExpect(jsonPath("$.refreshToken").value("new-secret"));
    }

    @Test
    void refresh_invalidToken_returnsUnauthorized() throws Exception {
        when(sessionApplicationService.rotate(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"bogus"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_blankToken_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"  "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logout_withoutAuthentication_returnsUnauthorized() throws Exception {
        // Unlike /login and /refresh, logout acts on the caller's own validated session, so it
        // must not be reachable anonymously.
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_authenticated_revokesTheCallersOwnSession() throws Exception {
        SessionId sessionId = SessionId.generate();
        JwtAuthenticationToken principal = new JwtAuthenticationToken(
                UserId.generate(), TenantId.of(tenantId),
                List.of(new SimpleGrantedAuthority("ROLE_USER")), sessionId);

        mockMvc.perform(post("/api/v1/auth/logout").with(authentication(principal)))
                .andExpect(status().isNoContent());

        verify(sessionApplicationService).revoke(sessionId);
    }
}
