package com.mudassirshahzad.eka.domain.generation.model;

import java.util.Objects;

public record LlmResponse(
        String       generatedText,
        FinishReason finishReason,
        String       modelName,
        int          promptTokens,
        int          completionTokens,
        long         latencyMs
) {

    public LlmResponse {
        Objects.requireNonNull(generatedText, "generatedText must not be null");
        Objects.requireNonNull(finishReason,  "finishReason must not be null");
        Objects.requireNonNull(modelName,     "modelName must not be null");
    }

    public int totalTokens() {
        return promptTokens + completionTokens;
    }
}
