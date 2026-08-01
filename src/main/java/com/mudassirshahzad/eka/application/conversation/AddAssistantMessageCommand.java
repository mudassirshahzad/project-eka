package com.mudassirshahzad.eka.application.conversation;

import com.mudassirshahzad.eka.domain.conversation.Citation;
import com.mudassirshahzad.eka.domain.conversation.ConversationId;
import com.mudassirshahzad.eka.domain.user.UserId;

import java.util.List;

/**
 * Command to persist a generated assistant reply — the write-side counterpart to
 * {@link AddUserMessageCommand}, kept deliberately symmetrical (ADR O02).
 *
 * @param conversationId the conversation this reply belongs to
 * @param userId         the owning user, used the same way {@link AddUserMessageCommand}
 *                        uses it — to verify conversation ownership before mutation
 * @param content        the guardrail-checked generated text
 * @param citations      citations resolved for this reply; may be empty, never null
 */
public record AddAssistantMessageCommand(
        ConversationId conversationId,
        UserId         userId,
        String         content,
        List<Citation> citations
) {}
