package com.mudassirshahzad.eka.application.orchestration;

import com.mudassirshahzad.eka.domain.conversation.Message;
import com.mudassirshahzad.eka.domain.generation.model.GeneratedResponse;

import java.util.Objects;

/**
 * Result of one full RAG turn — the persisted assistant {@link Message} (identity, content,
 * citations, timestamp) alongside the {@link GeneratedResponse} metadata that isn't part of the
 * conversation record itself (model name, token counts, latency).
 */
public record RagTurnResult(
        Message           assistantMessage,
        GeneratedResponse generatedResponse
) {
    public RagTurnResult {
        Objects.requireNonNull(assistantMessage,  "assistantMessage must not be null");
        Objects.requireNonNull(generatedResponse, "generatedResponse must not be null");
    }
}
