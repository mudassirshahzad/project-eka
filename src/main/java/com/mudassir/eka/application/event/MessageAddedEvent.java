package com.mudassir.eka.application.event;

import com.mudassir.eka.domain.conversation.ConversationId;
import com.mudassir.eka.domain.conversation.MessageRole;
import com.mudassir.eka.domain.shared.DomainEvent;
import com.mudassir.eka.domain.user.UserId;

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
