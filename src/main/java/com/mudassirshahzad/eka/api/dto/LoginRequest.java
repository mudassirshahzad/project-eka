package com.mudassirshahzad.eka.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LoginRequest(
        @NotNull  UUID   tenantId,
        @NotBlank String email,
        @NotBlank String password
) {}
