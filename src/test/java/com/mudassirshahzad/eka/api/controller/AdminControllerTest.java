package com.mudassirshahzad.eka.api.controller;

import com.mudassirshahzad.eka.api.config.SecurityConfig;
import com.mudassirshahzad.eka.api.config.WebMvcConfig;
import com.mudassirshahzad.eka.api.observability.CorrelationIdFilter;
import com.mudassirshahzad.eka.api.security.AuthorizationInterceptor;
import com.mudassirshahzad.eka.api.security.JwtAuthenticationFilter;
import com.mudassirshahzad.eka.api.security.JwtAuthenticationToken;
import com.mudassirshahzad.eka.api.security.JwtTokenProvider;
import com.mudassirshahzad.eka.api.security.RequestSizeLimitFilter;
import com.mudassirshahzad.eka.api.security.RestAuthenticationEntryPoint;
import com.mudassirshahzad.eka.application.shared.DuplicateResourceException;
import com.mudassirshahzad.eka.application.user.DeactivateUserUseCase;
import com.mudassirshahzad.eka.application.user.GetUserUseCase;
import com.mudassirshahzad.eka.application.user.RegisterUserUseCase;
import com.mudassirshahzad.eka.application.user.UserApplicationService;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.User;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.domain.user.UserRole;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} slice for {@link AdminController} (P06.1) — same recipe as
 * {@code ConversationControllerTest}. Also exercises {@link SecurityConfig}'s
 * {@code /api/v1/admin/bootstrap} public-endpoint entry (no {@code .with(authenticated())}).
 */
@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        WebMvcConfig.class, AuthorizationInterceptor.class, CorrelationIdFilter.class,
        RequestSizeLimitFilter.class, AdminControllerTest.MeterRegistryTestConfig.class})
class AdminControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private RegisterUserUseCase    registerUserUseCase;
    @MockitoBean private GetUserUseCase         getUserUseCase;
    @MockitoBean private DeactivateUserUseCase  deactivateUserUseCase;
    @MockitoBean private UserApplicationService userApplicationService;
    @MockitoBean private PasswordEncoder        passwordEncoder;
    @MockitoBean private JwtTokenProvider       jwtTokenProvider;

    @TestConfiguration
    static class MeterRegistryTestConfig {
        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private final UUID userId   = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    private RequestPostProcessor authenticatedAsAdmin() {
        return authenticatedAs("ROLE_ADMIN");
    }

    private RequestPostProcessor authenticatedAs(String... authorities) {
        List<SimpleGrantedAuthority> granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toList();
        return authentication(new JwtAuthenticationToken(UserId.of(userId), TenantId.of(tenantId), granted));
    }

    private User sampleUser(TenantId tenant) {
        return User.create(tenant, "user@example.com", "hashed", EnumSet.of(UserRole.USER));
    }

    // ── POST /admin/bootstrap ────────────────────────────────────────────────

    @Test
    void bootstrap_emptyTenant_returnsCreated() throws Exception {
        when(userApplicationService.tenantHasAnyUser(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        User admin = User.create(TenantId.of(tenantId), "admin@example.com", "hashed", EnumSet.of(UserRole.ADMIN));
        when(registerUserUseCase.execute(any())).thenReturn(admin);

        mockMvc.perform(post("/api/v1/admin/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","email":"admin@example.com","password":"secret123"}
                                """.formatted(tenantId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/admin/users/" + admin.getId().value()))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
    }

    @Test
    void bootstrap_alreadyInitializedTenant_returnsBadRequest() throws Exception {
        when(userApplicationService.tenantHasAnyUser(any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/admin/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","email":"admin@example.com","password":"secret123"}
                                """.formatted(tenantId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bootstrap_blankEmail_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/admin/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","email":"","password":"secret123"}
                                """.formatted(tenantId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bootstrap_shortPassword_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/admin/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","email":"admin@example.com","password":"short"}
                                """.formatted(tenantId)))
                .andExpect(status().isBadRequest());
    }

    // ── POST /admin/users ─────────────────────────────────────────────────────

    @Test
    void registerUser_adminRole_returnsCreated() throws Exception {
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        User user = sampleUser(TenantId.of(tenantId));
        when(registerUserUseCase.execute(any())).thenReturn(user);

        mockMvc.perform(post("/api/v1/admin/users")
                        .with(authenticatedAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"secret123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    @Test
    void registerUser_userRole_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .with(authenticatedAs("ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"secret123"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerUser_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"secret123"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerUser_duplicateEmail_returnsConflict() throws Exception {
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(registerUserUseCase.execute(any()))
                .thenThrow(new DuplicateResourceException(
                        "User with email 'user@example.com' already exists in this tenant"));

        mockMvc.perform(post("/api/v1/admin/users")
                        .with(authenticatedAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"secret123"}
                                """))
                .andExpect(status().isConflict());
    }

    // ── GET /admin/users/{id} ─────────────────────────────────────────────────

    @Test
    void getUser_adminRole_returnsUser() throws Exception {
        when(getUserUseCase.execute(any(), any())).thenReturn(sampleUser(TenantId.of(tenantId)));

        mockMvc.perform(get("/api/v1/admin/users/{id}", UUID.randomUUID()).with(authenticatedAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    @Test
    void getUser_userRole_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/{id}", UUID.randomUUID()).with(authenticatedAs("ROLE_USER")))
                .andExpect(status().isForbidden());
    }

    // ── POST /admin/users/{id}/deactivate ────────────────────────────────────

    @Test
    void deactivateUser_adminRole_returnsUpdatedUser() throws Exception {
        User user = sampleUser(TenantId.of(tenantId));
        user.deactivate();
        when(deactivateUserUseCase.execute(any(), any())).thenReturn(user);

        mockMvc.perform(post("/api/v1/admin/users/{id}/deactivate", UUID.randomUUID()).with(authenticatedAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deactivateUser_userRole_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/{id}/deactivate", UUID.randomUUID())
                        .with(authenticatedAs("ROLE_USER")))
                .andExpect(status().isForbidden());
    }
}
