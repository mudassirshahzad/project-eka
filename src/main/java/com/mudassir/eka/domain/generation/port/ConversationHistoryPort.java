package com.mudassir.eka.domain.generation.port;

import com.mudassir.eka.domain.conversation.ConversationId;
import com.mudassir.eka.domain.conversation.Message;
import com.mudassir.eka.domain.shared.TenantId;

import java.util.List;

/**
 * Port for retrieving recent conversation history for memory-augmented generation.
 *
 * <p>This is a read-only port. Writing conversation history is a separate concern and
 * must not be mixed into this interface (ADR M01).
 *
 * <p>Implementations may be backed by any storage technology — in-memory, PostgreSQL,
 * Redis, MongoDB, or an external memory service — without any change to the application
 * or domain layers.
 *
 * <p>The returned list is ordered chronologically (oldest message first) and contains
 * at most {@code maxMessages} entries. If the conversation does not exist, or has no
 * messages, an empty list is returned — never null, never an exception.
 */
public interface ConversationHistoryPort {

    /**
     * Returns the most recent messages from a conversation, up to {@code maxMessages}.
     *
     * @param conversationId the conversation to retrieve history for; must not be null
     * @param tenantId       the owning tenant, for isolation enforcement; must not be null
     * @param maxMessages    maximum number of messages to return; must be &gt;= 0
     * @return an unmodifiable chronological list of recent messages; never null
     */
    List<Message> getRecentMessages(ConversationId conversationId, TenantId tenantId, int maxMessages);
}
