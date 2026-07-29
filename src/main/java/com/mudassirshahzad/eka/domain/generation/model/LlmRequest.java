package com.mudassirshahzad.eka.domain.generation.model;

import java.util.Objects;

public record LlmRequest(
        PromptRequest     promptRequest,
        GenerationOptions options
) {

    public LlmRequest {
        Objects.requireNonNull(promptRequest, "promptRequest must not be null");
        Objects.requireNonNull(options,       "options must not be null");
    }
}
