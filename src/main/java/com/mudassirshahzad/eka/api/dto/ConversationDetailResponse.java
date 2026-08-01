package com.mudassirshahzad.eka.api.dto;

import com.mudassirshahzad.eka.domain.conversation.Conversation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ConversationDetailResponse(
        UUID                  id,
        String                title,
        List<MessageResponse> messages,
        Instant                createdAt
) {

    public ConversationDetailResponse {
        Objects.requireNonNull(id,        "id must not be null");
        messages = messages != null ? List.copyOf(messages) : List.of();
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static ConversationDetailResponse from(Conversation conversation) {
        return new ConversationDetailResponse(
                conversation.getId().value(),
                conversation.getTitle(),
                conversation.getMessages().stream().map(MessageResponse::from).toList(),
                conversation.getCreatedAt());
    }
}
