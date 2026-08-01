package com.mudassirshahzad.eka.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * {@code tenantId}/{@code userId} are explicit request fields, not derived from an authenticated
 * principal — there is no authentication in P05.1 (ADR O05). P05.2 replaces both with values
 * extracted from the validated JWT.
 */
public record CreateConversationRequest(
        @NotNull UUID tenantId,
        @NotNull UUID userId,
        @NotBlank @Size(max = 500) String title
) {}
