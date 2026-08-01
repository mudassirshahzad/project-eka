package com.mudassirshahzad.eka.api.dto;

import com.mudassirshahzad.eka.application.orchestration.RagTurnResult;
import com.mudassirshahzad.eka.domain.conversation.Message;
import com.mudassirshahzad.eka.domain.generation.model.GeneratedResponse;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record GeneratedAnswerResponse(
        UUID                   messageId,
        String                 content,
        List<CitationResponse> citations,
        String                 modelName,
        int                    totalTokens,
        long                   latencyMs,
        Instant                createdAt
) {

    public GeneratedAnswerResponse {
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(content,   "content must not be null");
        citations = citations != null ? List.copyOf(citations) : List.of();
        Objects.requireNonNull(modelName, "modelName must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static GeneratedAnswerResponse from(RagTurnResult result) {
        Message            message   = result.assistantMessage();
        GeneratedResponse  generated = result.generatedResponse();
        return new GeneratedAnswerResponse(
                message.id(),
                message.content(),
                message.citations().stream().map(CitationResponse::from).toList(),
                generated.modelName(),
                generated.totalTokens(),
                generated.latencyMs(),
                message.createdAt());
    }
}
