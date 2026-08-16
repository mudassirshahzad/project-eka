package com.mudassirshahzad.eka.application.conversation;

import com.mudassirshahzad.eka.domain.conversation.Conversation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * v0.6.1, ADR EX08: this is now the class {@code ConversationController.createConversation}
 * actually calls — previously it existed unreached (the controller called
 * {@code ConversationApplicationService} directly) while independently re-validating the exact
 * title-length rule the REST DTO already enforced via Bean Validation. That duplicate,
 * unreachable check has been removed: {@link Conversation#create} is now the sole, authoritative
 * enforcement point for the title invariant, so it is honored regardless of caller. What remains
 * here — null-guards on identity, structured logging, delegation — is genuine orchestration value
 * distinct from {@link ConversationApplicationService}'s own responsibility (persistence + event
 * publication), which is why this class stays rather than being deleted (contrast
 * {@code GetConversationUseCase}/{@code ListConversationsUseCase}, removed this same milestone for
 * adding no value beyond what they wrapped).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CreateConversationUseCase {

    private final ConversationApplicationService conversationService;

    public Conversation execute(CreateConversationCommand cmd) {
        Objects.requireNonNull(cmd, "command must not be null");
        Objects.requireNonNull(cmd.userId(), "userId must not be null");
        Objects.requireNonNull(cmd.tenantId(), "tenantId must not be null");

        log.debug("Creating conversation: user={} tenant={}", cmd.userId(), cmd.tenantId());
        return conversationService.createConversation(cmd);
    }
}
