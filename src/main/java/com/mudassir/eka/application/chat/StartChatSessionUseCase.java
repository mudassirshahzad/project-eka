package com.mudassir.eka.application.chat;

import com.mudassir.eka.application.shared.ApplicationException;
import com.mudassir.eka.domain.conversation.ChatSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StartChatSessionUseCase {

    private final ChatSessionApplicationService chatSessionService;

    public ChatSession execute(StartChatSessionCommand cmd) {
        Objects.requireNonNull(cmd, "command must not be null");
        Objects.requireNonNull(cmd.conversationId(), "conversationId must not be null");
        Objects.requireNonNull(cmd.userId(), "userId must not be null");
        Objects.requireNonNull(cmd.tenantId(), "tenantId must not be null");

        if (cmd.modelId() == null || cmd.modelId().isBlank()) {
            throw new ApplicationException("modelId must not be blank");
        }

        log.debug("Starting chat session: conversation={} model={}", cmd.conversationId(), cmd.modelId());
        return chatSessionService.startSession(cmd);
    }
}
