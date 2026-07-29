package com.mudassirshahzad.eka.application.conversation;

import com.mudassirshahzad.eka.domain.conversation.ConversationId;
import com.mudassirshahzad.eka.domain.user.UserId;

public record AddUserMessageCommand(
        ConversationId conversationId,
        UserId         userId,
        String         content
) {}
