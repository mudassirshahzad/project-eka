package com.mudassir.eka.application.query;

import com.mudassir.eka.domain.conversation.ConversationId;
import com.mudassir.eka.domain.query.MetadataFilter;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;

public record SubmitQueryCommand(
        UserId         userId,
        TenantId       tenantId,
        ConversationId conversationId,
        String         queryText,
        MetadataFilter filter
) {}
