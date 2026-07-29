package com.mudassirshahzad.eka.application.query;

import com.mudassirshahzad.eka.domain.conversation.ConversationId;
import com.mudassirshahzad.eka.domain.query.MetadataFilter;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;

public record SubmitQueryCommand(
        UserId         userId,
        TenantId       tenantId,
        ConversationId conversationId,
        String         queryText,
        MetadataFilter filter
) {}
