package com.mudassirshahzad.eka.application.chat;

import com.mudassirshahzad.eka.domain.conversation.ChatSessionId;

public record RecordTurnCommand(
        ChatSessionId sessionId,
        int           promptTokens,
        int           completionTokens,
        long          latencyMs
) {}
