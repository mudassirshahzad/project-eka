package com.mudassir.eka.application.chat;

import com.mudassir.eka.domain.conversation.ConversationId;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;

public record StartChatSessionCommand(
        ConversationId conversationId,
        UserId         userId,
        TenantId       tenantId,
        String         modelId
) {}
