package com.mudassirshahzad.eka.api.dto;

import com.mudassirshahzad.eka.domain.conversation.Message;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MessageResponse(
        UUID                   id,
        String                 role,
        String                 content,
        List<CitationResponse> citations,
        Instant                createdAt
) {

    public MessageResponse {
        Objects.requireNonNull(id,        "id must not be null");
        Objects.requireNonNull(role,      "role must not be null");
        Objects.requireNonNull(content,   "content must not be null");
        citations = citations != null ? List.copyOf(citations) : List.of();
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.id(),
                message.role().name(),
                message.content(),
                message.citations().stream().map(CitationResponse::from).toList(),
                message.createdAt());
    }
}
