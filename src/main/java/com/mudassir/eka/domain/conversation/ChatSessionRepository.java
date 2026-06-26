package com.mudassir.eka.domain.conversation;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository {

    ChatSession save(ChatSession session);

    Optional<ChatSession> findById(ChatSessionId id);

    /**
     * Returns the single ACTIVE session for a conversation, if any.
     * At most one session per conversation should be ACTIVE at a time.
     */
    Optional<ChatSession> findActiveByConversationId(ConversationId conversationId);

    /**
     * Returns all sessions for a conversation ordered by startedAt ascending.
     */
    List<ChatSession> findByConversationId(ConversationId conversationId);
}
