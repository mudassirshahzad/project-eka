package com.mudassir.eka.application.event;

import com.mudassir.eka.domain.conversation.ChatSessionId;
import com.mudassir.eka.domain.conversation.ConversationId;
import com.mudassir.eka.domain.shared.DomainEvent;

public class ChatSessionTimedOutEvent extends DomainEvent {

    private final ChatSessionId  sessionId;
    private final ConversationId conversationId;

    public ChatSessionTimedOutEvent(ChatSessionId sessionId, ConversationId conversationId) {
        super();
        this.sessionId      = sessionId;
        this.conversationId = conversationId;
    }

    @Override
    public String getEventType() { return "chat.session.timed_out"; }

    public ChatSessionId  getSessionId()      { return sessionId; }
    public ConversationId getConversationId() { return conversationId; }
}
