package com.mudassirshahzad.eka.application.chat;

import com.mudassirshahzad.eka.domain.conversation.ConversationId;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;

public record StartChatSessionCommand(
        ConversationId conversationId,
        UserId         userId,
        TenantId       tenantId,
        String         modelId
) {}
