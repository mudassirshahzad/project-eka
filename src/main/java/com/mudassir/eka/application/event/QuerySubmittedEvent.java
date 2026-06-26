package com.mudassir.eka.application.event;

import com.mudassir.eka.domain.conversation.ConversationId;
import com.mudassir.eka.domain.query.QueryId;
import com.mudassir.eka.domain.shared.DomainEvent;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;

public class QuerySubmittedEvent extends DomainEvent {

    private final QueryId        queryId;
    private final UserId         userId;
    private final TenantId       tenantId;
    private final ConversationId conversationId;
    private final String         queryText;

    public QuerySubmittedEvent(QueryId queryId, UserId userId, TenantId tenantId,
                                ConversationId conversationId, String queryText) {
        super();
        this.queryId        = queryId;
        this.userId         = userId;
        this.tenantId       = tenantId;
        this.conversationId = conversationId;
        this.queryText      = queryText;
    }

    @Override
    public String getEventType() { return "query.submitted"; }

    public QueryId        getQueryId()        { return queryId; }
    public UserId         getUserId()         { return userId; }
    public TenantId       getTenantId()       { return tenantId; }
    public ConversationId getConversationId() { return conversationId; }
    public String         getQueryText()      { return queryText; }
}
