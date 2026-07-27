package com.mudassir.eka.application.generation;

import com.mudassir.eka.domain.generation.model.GenerationOptions;
import com.mudassir.eka.domain.retrieval.model.AssembledContext;
import com.mudassir.eka.domain.shared.TenantId;

import java.util.Objects;

public record GenerationRequest(
        AssembledContext  assembledContext,
        String            originalQueryText,
        TenantId          tenantId,
        GenerationOptions options
) {

    public GenerationRequest {
        Objects.requireNonNull(assembledContext,  "assembledContext must not be null");
        Objects.requireNonNull(originalQueryText, "originalQueryText must not be null");
        if (originalQueryText.isBlank())
            throw new IllegalArgumentException("originalQueryText must not be blank");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        options = options != null ? options : GenerationOptions.DEFAULT;
    }
}
