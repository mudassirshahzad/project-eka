package com.mudassirshahzad.eka.application.event;

import com.mudassirshahzad.eka.domain.conversation.ConversationId;
import com.mudassirshahzad.eka.domain.shared.DomainEvent;
import com.mudassirshahzad.eka.domain.user.UserId;

public class ConversationDeletedEvent extends DomainEvent {

    private final ConversationId conversationId;
    private final UserId         userId;

    public ConversationDeletedEvent(ConversationId conversationId, UserId userId) {
        super();
        this.conversationId = conversationId;
        this.userId         = userId;
    }

    @Override
    public String getEventType() { return "conversation.deleted"; }

    public ConversationId getConversationId() { return conversationId; }
    public UserId         getUserId()         { return userId; }
}
