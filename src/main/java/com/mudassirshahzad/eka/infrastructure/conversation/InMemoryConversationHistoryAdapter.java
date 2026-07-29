package com.mudassirshahzad.eka.infrastructure.conversation;

import com.mudassirshahzad.eka.domain.conversation.ConversationId;
import com.mudassirshahzad.eka.domain.conversation.Message;
import com.mudassirshahzad.eka.domain.generation.port.ConversationHistoryPort;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Development-grade {@link ConversationHistoryPort} backed by an in-memory store.
 *
 * <p>This adapter is intentionally simple. It is the designated seam point for future
 * replacement by a durable storage implementation (PostgreSQL, Redis, MongoDB, or an
 * external memory service) without any change to the application or domain layers (ADR M04).
 *
 * <p>Thread safety: all read and write operations are synchronized on {@code this}. For
 * a dev-grade adapter this is correct and avoids hidden races between {@link #addMessage}
 * and {@link #getRecentMessages}.
 *
 * <p>This adapter is read-oriented: {@link #getRecentMessages} is the port method.
 * {@link #addMessage} and {@link #clearConversation} are infrastructure-level operations
 * for seeding conversations (from API handlers or tests) and are not part of the port
 * contract.
 *
 * <p>Logging: conversation IDs and message counts are logged at DEBUG.
 * Message content is never logged.
 */
@Slf4j
@Component
public class InMemoryConversationHistoryAdapter implements ConversationHistoryPort {

    private final Map<ConversationId, List<Message>> store = new HashMap<>();

    @Override
    public synchronized List<Message> getRecentMessages(
            ConversationId conversationId, TenantId tenantId, int maxMessages) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(tenantId,       "tenantId must not be null");
        if (maxMessages < 0)
            throw new IllegalArgumentException("maxMessages must be >= 0 but was " + maxMessages);

        if (maxMessages == 0) {
            return List.of();
        }

        List<Message> all = store.getOrDefault(conversationId, List.of());
        int size = all.size();
        int from = Math.max(0, size - maxMessages);
        List<Message> window = List.copyOf(all.subList(from, size));

        log.debug("Conversation history fetched: conversationId={} requested={} returned={}",
                conversationId, maxMessages, window.size());
        return window;
    }

    /**
     * Appends a message to the conversation history.
     *
     * <p>Not part of {@link ConversationHistoryPort}. Intended for infrastructure-layer
     * callers (REST handlers, tests) that need to seed or record new messages. Future
     * durable adapters will replace this with writes to their respective storage backend.
     */
    public synchronized void addMessage(ConversationId conversationId, Message message) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(message,        "message must not be null");
        store.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(message);
        log.debug("Message added to conversation: conversationId={} role={}", conversationId, message.role());
    }

    /**
     * Removes all messages for the given conversation.
     *
     * <p>Not part of {@link ConversationHistoryPort}. Intended for test cleanup or
     * administrative operations.
     */
    public synchronized void clearConversation(ConversationId conversationId) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        store.remove(conversationId);
        log.debug("Conversation history cleared: conversationId={}", conversationId);
    }
}
