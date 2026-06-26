package com.mudassir.eka.application.event;

import com.mudassir.eka.domain.conversation.ChatSessionId;
import com.mudassir.eka.domain.conversation.ConversationId;
import com.mudassir.eka.domain.shared.DomainEvent;

public class ChatSessionCompletedEvent extends DomainEvent {

    private final ChatSessionId  sessionId;
    private final ConversationId conversationId;
    private final int            totalTokens;

    public ChatSessionCompletedEvent(ChatSessionId sessionId, ConversationId conversationId,
                                      int totalTokens) {
        super();
        this.sessionId      = sessionId;
        this.conversationId = conversationId;
        this.totalTokens    = totalTokens;
    }

    @Override
    public String getEventType() { return "chat.session.completed"; }

    public ChatSessionId  getSessionId()      { return sessionId; }
    public ConversationId getConversationId() { return conversationId; }
    public int            getTotalTokens()    { return totalTokens; }
}
