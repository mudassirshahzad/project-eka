package com.mudassirshahzad.eka.application.conversation;

import com.mudassirshahzad.eka.application.chat.ChatSessionApplicationService;
import com.mudassirshahzad.eka.application.shared.ApplicationException;
import com.mudassirshahzad.eka.domain.conversation.ConversationId;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
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

    public void execute(ConversationId id, UserId userId, TenantId tenantId) {
        Objects.requireNonNull(id, "conversationId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        chatSessionService.findActiveSession(id).ifPresent(active -> {
            throw new ApplicationException(
                    "Cannot delete conversation with an active session: " + active.getId());
        });

        log.debug("Deleting conversation: id={} user={}", id, userId);
        conversationService.deleteConversation(id, userId, tenantId);
    }
}
