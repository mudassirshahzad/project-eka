package com.mudassir.eka.application.event;

import com.mudassir.eka.domain.conversation.ConversationId;
import com.mudassir.eka.domain.shared.DomainEvent;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;

public class ConversationCreatedEvent extends DomainEvent {

    private final ConversationId conversationId;
    private final UserId         userId;
    private final TenantId       tenantId;
    private final String         title;

    public ConversationCreatedEvent(ConversationId conversationId, UserId userId,
                                     TenantId tenantId, String title) {
        super();
        this.conversationId = conversationId;
        this.userId         = userId;
        this.tenantId       = tenantId;
        this.title          = title;
    }

    @Override
    public String getEventType() { return "conversation.created"; }

    public ConversationId getConversationId() { return conversationId; }
    public UserId         getUserId()         { return userId; }
    public TenantId       getTenantId()       { return tenantId; }
    public String         getTitle()          { return title; }
}
