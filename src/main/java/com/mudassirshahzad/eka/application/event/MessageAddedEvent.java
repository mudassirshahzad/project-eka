package com.mudassirshahzad.eka.application.event;

import com.mudassirshahzad.eka.domain.conversation.ConversationId;
import com.mudassirshahzad.eka.domain.conversation.MessageRole;
import com.mudassirshahzad.eka.domain.shared.DomainEvent;
import com.mudassirshahzad.eka.domain.user.UserId;

import java.util.UUID;

public class MessageAddedEvent extends DomainEvent {

    private final ConversationId conversationId;
    private final UUID           messageId;
    private final UserId         userId;
    private final MessageRole    role;

    public MessageAddedEvent(ConversationId conversationId, UUID messageId,
                              UserId userId, MessageRole role) {
        super();
        this.conversationId = conversationId;
        this.messageId      = messageId;
        this.userId         = userId;
        this.role           = role;
    }

    @Override
    public String getEventType() { return "conversation.message.added"; }

    public ConversationId getConversationId() { return conversationId; }
    public UUID           getMessageId()      { return messageId; }
    public UserId         getUserId()         { return userId; }
    public MessageRole    getRole()           { return role; }
}
