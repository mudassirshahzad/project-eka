package com.mudassirshahzad.eka.api.controller;

import com.mudassirshahzad.eka.api.config.SecurityConfig;
import com.mudassirshahzad.eka.api.security.JwtAuthenticationFilter;
import com.mudassirshahzad.eka.api.security.JwtProperties;
import com.mudassirshahzad.eka.api.security.JwtTokenProvider;
import com.mudassirshahzad.eka.api.security.RestAuthenticationEntryPoint;
import com.mudassirshahzad.eka.application.shared.InvalidCredentialsException;
import com.mudassirshahzad.eka.application.user.AuthenticateUserUseCase;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.User;
import com.mudassirshahzad.eka.domain.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.EnumSet;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} slice covering {@link AuthController} against the real
 * {@link SecurityConfig} filter chain — proves {@code POST /api/v1/auth/login} is genuinely
 * {@code permitAll} (no Authorization header attached to any request here) and that failed
 * login attempts surface as 401 via {@link com.mudassirshahzad.eka.api.exception.GlobalExceptionHandler}.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class})
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AuthenticateUserUseCase authenticateUserUseCase;
    @MockitoBean private JwtTokenProvider         jwtTokenProvider;
    @MockitoBean private JwtProperties            jwtProperties;

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
}
