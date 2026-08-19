package com.mudassirshahzad.eka.api.controller;

import com.mudassirshahzad.eka.api.dto.BootstrapRequest;
import com.mudassirshahzad.eka.api.dto.RegisterUserRequest;
import com.mudassirshahzad.eka.api.dto.UserResponse;
import com.mudassirshahzad.eka.api.security.JwtAuthenticationToken;
import com.mudassirshahzad.eka.api.security.RequireRole;
import com.mudassirshahzad.eka.application.shared.ApplicationException;
import com.mudassirshahzad.eka.application.user.DeactivateUserUseCase;
import com.mudassirshahzad.eka.application.user.GetUserUseCase;
import com.mudassirshahzad.eka.application.user.RegisterUserCommand;
import com.mudassirshahzad.eka.application.user.RegisterUserUseCase;
import com.mudassirshahzad.eka.application.user.UserApplicationService;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.User;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.domain.user.UserRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

/**
 * Minimum REST surface for first-user bootstrap and user administration (P06.1) — reuses
 * {@code RegisterUserUseCase}/{@code GetUserUseCase}/{@code DeactivateUserUseCase} exactly as they
 * already existed; no user-management business logic lives here.
 *
 * <p>Deliberately minimal: no list-users, no role-assignment, no password-change endpoint — those
 * are real capabilities {@code UserApplicationService} already has, but exposing them wasn't asked
 * for and isn't needed to close this milestone's "administration" scope (bootstrap, register, get,
 * deactivate). Adding them without a concrete need would be exactly the kind of unnecessary
 * user-management surface this milestone's brief explicitly warns against.
 *
 * <p><b>No tenant-provisioning endpoint exists</b> (ADR PC03) — there is no {@code Tenant} domain
 * aggregate or repository port in this codebase; tenant creation stays an ops/database concern, as
 * it already was before this milestone. {@link #bootstrap} therefore operates against an
 * already-provisioned, still-empty tenant, not a newly-created one.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final RegisterUserUseCase    registerUserUseCase;
    private final GetUserUseCase         getUserUseCase;
    private final DeactivateUserUseCase  deactivateUserUseCase;
    private final UserApplicationService userApplicationService;
    private final PasswordEncoder        passwordEncoder;

    /**
     * Public (no authentication) — deliberately so: the very first admin of a tenant cannot
     * authenticate as anything yet. Safe to leave public because it only ever succeeds once per
     * tenant: {@link UserApplicationService#tenantHasAnyUser} rejects every call after the first
     * (ADR PC03). A caller needs to already know a real, ops-provisioned, not-yet-bootstrapped
     * tenant UUID — tenant creation itself is not exposed anywhere in this API.
     */
    @PostMapping("/bootstrap")
    public ResponseEntity<UserResponse> bootstrap(@Valid @RequestBody BootstrapRequest request) {
        TenantId tenantId = TenantId.of(request.tenantId());

        if (userApplicationService.tenantHasAnyUser(tenantId)) {
            throw new ApplicationException("Tenant is already initialized");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User admin = registerUserUseCase.execute(new RegisterUserCommand(
                tenantId, request.email(), passwordHash, Set.of(UserRole.ADMIN)));

        log.info("Tenant bootstrapped: tenant={} adminUser={}", tenantId, admin.getId());
        UserResponse response = UserResponse.from(admin);
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + response.id())).body(response);
    }

    @PostMapping("/users")
    @RequireRole(UserRole.ADMIN)
    public ResponseEntity<UserResponse> registerUser(
            Authentication authentication, @Valid @RequestBody RegisterUserRequest request) {

        JwtAuthenticationToken principal = (JwtAuthenticationToken) authentication;
        String passwordHash = passwordEncoder.encode(request.password());

        User user = registerUserUseCase.execute(new RegisterUserCommand(
                principal.tenantId(), request.email(), passwordHash, request.roles()));

        UserResponse response = UserResponse.from(user);
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + response.id())).body(response);
    }

    @GetMapping("/users/{userId}")
    @RequireRole(UserRole.ADMIN)
    public UserResponse getUser(Authentication authentication, @PathVariable UUID userId) {
        JwtAuthenticationToken principal = (JwtAuthenticationToken) authentication;
        User user = getUserUseCase.execute(UserId.of(userId), principal.tenantId());
        return UserResponse.from(user);
    }

    @PostMapping("/users/{userId}/deactivate")
    @RequireRole(UserRole.ADMIN)
    public UserResponse deactivateUser(Authentication authentication, @PathVariable UUID userId) {
        JwtAuthenticationToken principal = (JwtAuthenticationToken) authentication;
        User user = deactivateUserUseCase.execute(UserId.of(userId), principal.tenantId());
        return UserResponse.from(user);
    }
}
