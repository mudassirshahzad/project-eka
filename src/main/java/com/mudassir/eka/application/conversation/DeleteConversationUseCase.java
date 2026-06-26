package com.mudassir.eka.application.conversation;

import com.mudassir.eka.application.chat.ChatSessionApplicationService;
import com.mudassir.eka.application.shared.ApplicationException;
import com.mudassir.eka.domain.conversation.ConversationId;
import com.mudassir.eka.domain.user.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DeleteConversationUseCase {

    private final ConversationApplicationService conversationService;
    private final ChatSessionApplicationService  chatSessionService;

    public void execute(ConversationId id, UserId userId) {
        Objects.requireNonNull(id, "conversationId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");

        chatSessionService.findActiveSession(id).ifPresent(active -> {
            throw new ApplicationException(
                    "Cannot delete conversation with an active session: " + active.getId());
        });

        log.debug("Deleting conversation: id={} user={}", id, userId);
        conversationService.deleteConversation(id, userId);
    }
}
