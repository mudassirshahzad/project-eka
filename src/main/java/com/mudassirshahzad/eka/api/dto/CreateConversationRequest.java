package com.mudassirshahzad.eka.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code tenantId}/{@code userId} are resolved from the authenticated principal
 * ({@code JwtAuthenticationToken}), not request fields — P05.2 replaced the P05.1 placeholder
 * fields with real identity extracted from the validated JWT (ADR A05).
 */
public record CreateConversationRequest(
        @NotBlank @Size(max = 500) String title
) {}
