package com.mudassirshahzad.eka.api.dto;

import com.mudassirshahzad.eka.domain.conversation.Conversation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ConversationResponse(UUID id, String title, Instant createdAt) {

    public ConversationResponse {
        Objects.requireNonNull(id,        "id must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static ConversationResponse from(Conversation conversation) {
        return new ConversationResponse(
                conversation.getId().value(),
                conversation.getTitle(),
                conversation.getCreatedAt());
    }
}
