package com.mudassir.eka.application.conversation;

import com.mudassir.eka.application.shared.ApplicationException;
import com.mudassir.eka.domain.conversation.Conversation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CreateConversationUseCase {

    private static final int MAX_TITLE_LENGTH = 500;

    private final ConversationApplicationService conversationService;

    public Conversation execute(CreateConversationCommand cmd) {
        Objects.requireNonNull(cmd, "command must not be null");
        Objects.requireNonNull(cmd.userId(), "userId must not be null");
        Objects.requireNonNull(cmd.tenantId(), "tenantId must not be null");

        if (cmd.title() == null || cmd.title().isBlank()) {
            throw new ApplicationException("title must not be blank");
        }
        if (cmd.title().length() > MAX_TITLE_LENGTH) {
            throw new ApplicationException(
                    "title exceeds maximum length of " + MAX_TITLE_LENGTH + " characters");
        }

        log.debug("Creating conversation: user={} tenant={}", cmd.userId(), cmd.tenantId());
        return conversationService.createConversation(cmd);
    }
}
