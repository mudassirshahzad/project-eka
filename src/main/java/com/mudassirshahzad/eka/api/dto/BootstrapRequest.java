package com.mudassirshahzad.eka.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Creates the first {@code ADMIN} user for an already-provisioned, still-empty tenant (P06.1,
 * ADR PC03). {@code tenantId} is a request field, not JWT-derived — there is no authenticated
 * caller yet, which is exactly the bootstrap problem this endpoint exists to solve.
 */
public record BootstrapRequest(
        @NotNull  UUID   tenantId,
        @NotBlank String email,
        @NotBlank @Size(min = 8) String password
) {}
