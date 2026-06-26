package com.mudassir.eka.application.chat;

import com.mudassir.eka.domain.conversation.ChatSessionId;

public record RecordTurnCommand(
        ChatSessionId sessionId,
        int           promptTokens,
        int           completionTokens,
        long          latencyMs
) {}
