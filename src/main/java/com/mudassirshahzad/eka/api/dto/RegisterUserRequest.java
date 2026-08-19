package com.mudassirshahzad.eka.api.dto;

import com.mudassirshahzad.eka.domain.user.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Registers an additional user in the caller's own tenant — {@code ADMIN}-only (P06.1). Unlike
 * {@link BootstrapRequest}, {@code tenantId} is deliberately absent here: it comes from the
 * authenticated caller's JWT, never the request body (ADR A05).
 */
public record RegisterUserRequest(
        @NotBlank String email,
        @NotBlank @Size(min = 8) String password,
        Set<UserRole> roles
) {

    /** Defaults to {@code {USER}} when omitted — mirrors {@code DocumentMetadata}'s
     *  null-to-default compact-constructor pattern for an optional collection field. */
    public RegisterUserRequest {
        roles = (roles == null || roles.isEmpty()) ? Set.of(UserRole.USER) : Set.copyOf(roles);
    }
}
