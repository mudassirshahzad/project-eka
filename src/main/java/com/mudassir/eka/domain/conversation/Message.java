package com.mudassir.eka.domain.conversation;

import com.mudassir.eka.domain.query.QueryId;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record Message(
        UUID           id,
        MessageRole    role,
        String         content,
        List<Citation> citations,
        QueryId        queryId,
        Instant        createdAt
) {

    public Message {
        citations = citations != null ? Collections.unmodifiableList(citations) : List.of();
    }

    public static Message userMessage(String content) {
        return new Message(UUID.randomUUID(), MessageRole.USER, content, List.of(), null, Instant.now());
    }

    public static Message assistantMessage(String content, List<Citation> citations, QueryId queryId) {
        return new Message(UUID.randomUUID(), MessageRole.ASSISTANT, content, citations, queryId, Instant.now());
    }

    public static Message systemMessage(String content) {
        return new Message(UUID.randomUUID(), MessageRole.SYSTEM, content, List.of(), null, Instant.now());
    }

    public boolean isFromUser() {
        return role == MessageRole.USER;
    }

    public boolean hasCitations() {
        return !citations.isEmpty();
    }
}
