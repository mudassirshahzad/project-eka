package com.mudassirshahzad.eka.application.orchestration;

import com.mudassirshahzad.eka.domain.conversation.ConversationId;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;

import java.util.Objects;

/**
 * Command to run one full RAG turn: persist the user's message, retrieve context, generate a
 * guardrail-checked, cited answer, and persist that answer — see {@link RagOrchestrationService}.
 *
 * @param conversationId the conversation this message belongs to; must already exist
 * @param userId         the sending user, used for conversation-ownership verification
 * @param tenantId       owning tenant, threaded explicitly through retrieval and generation
 * @param content        the user's verbatim message text; must not be blank
 */
public record SendMessageCommand(
        ConversationId conversationId,
        UserId         userId,
        TenantId       tenantId,
        String         content
) {
    public SendMessageCommand {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(userId,         "userId must not be null");
        Objects.requireNonNull(tenantId,       "tenantId must not be null");
        Objects.requireNonNull(content,        "content must not be null");
        if (content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
    }
}
