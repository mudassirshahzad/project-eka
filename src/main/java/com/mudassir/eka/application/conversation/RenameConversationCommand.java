package com.mudassir.eka.application.conversation;

import com.mudassir.eka.domain.conversation.ConversationId;
import com.mudassir.eka.domain.user.UserId;

public record RenameConversationCommand(
        ConversationId conversationId,
        UserId         userId,
        String         newTitle
) {}
