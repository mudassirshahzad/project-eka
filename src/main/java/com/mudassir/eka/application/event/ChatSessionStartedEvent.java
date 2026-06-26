package com.mudassir.eka.application.event;

import com.mudassir.eka.domain.conversation.ChatSessionId;
import com.mudassir.eka.domain.conversation.ConversationId;
import com.mudassir.eka.domain.shared.DomainEvent;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;

public class ChatSessionStartedEvent extends DomainEvent {

    private final ChatSessionId  sessionId;
    private final ConversationId conversationId;
    private final UserId         userId;
    private final TenantId       tenantId;
    private final String         modelId;

    public ChatSessionStartedEvent(ChatSessionId sessionId, ConversationId conversationId,
                                    UserId userId, TenantId tenantId, String modelId) {
        super();
        this.sessionId      = sessionId;
        this.conversationId = conversationId;
        this.userId         = userId;
        this.tenantId       = tenantId;
        this.modelId        = modelId;
    }

    @Override
    public String getEventType() { return "chat.session.started"; }

    public ChatSessionId  getSessionId()      { return sessionId; }
    public ConversationId getConversationId() { return conversationId; }
    public UserId         getUserId()         { return userId; }
    public TenantId       getTenantId()       { return tenantId; }
    public String         getModelId()        { return modelId; }
}
