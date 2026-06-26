package com.mudassir.eka.application.chat;

import com.mudassir.eka.domain.conversation.ChatSession;
import com.mudassir.eka.domain.conversation.ChatSessionId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TimeoutChatSessionUseCase {

    private final ChatSessionApplicationService chatSessionService;

    public ChatSession execute(ChatSessionId id) {
        Objects.requireNonNull(id, "sessionId must not be null");
        log.debug("Timing out chat session: id={}", id);
        return chatSessionService.timeoutSession(id);
    }
}
