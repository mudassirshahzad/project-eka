package com.mudassirshahzad.eka.application.conversation;

import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;

public record CreateConversationCommand(
        UserId   userId,
        TenantId tenantId,
        String   title
) {}
