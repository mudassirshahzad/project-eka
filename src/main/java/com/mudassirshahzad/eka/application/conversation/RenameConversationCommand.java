package com.mudassirshahzad.eka.application.conversation;

import com.mudassirshahzad.eka.domain.conversation.ConversationId;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;

/**
 * @param tenantId used only to verify the fetched conversation belongs to the caller's tenant
 *                 (ADR TN01/HD02) — never used to scope the lookup itself, which stays
 *                 ownership-scoped via {@code userId}
 */
public record RenameConversationCommand(
        ConversationId conversationId,
        UserId         userId,
        TenantId       tenantId,
        String         newTitle
) {}
