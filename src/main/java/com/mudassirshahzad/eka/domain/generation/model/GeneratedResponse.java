package com.mudassirshahzad.eka.domain.generation.model;

import com.mudassirshahzad.eka.domain.conversation.Citation;

import java.util.List;
import java.util.Objects;

public record GeneratedResponse(
        String         generatedText,
        List<Citation> citations,
        String         modelName,
        int            totalTokens,
        long           latencyMs
) {

    public GeneratedResponse {
        Objects.requireNonNull(generatedText, "generatedText must not be null");
        Objects.requireNonNull(citations,     "citations must not be null");
        Objects.requireNonNull(modelName,     "modelName must not be null");
        citations = List.copyOf(citations);
    }

    public boolean hasCitations() {
        return !citations.isEmpty();
    }
}
