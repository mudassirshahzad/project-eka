package com.mudassir.eka.application.conversation;

import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;

public record CreateConversationCommand(
        UserId   userId,
        TenantId tenantId,
        String   title
) {}
