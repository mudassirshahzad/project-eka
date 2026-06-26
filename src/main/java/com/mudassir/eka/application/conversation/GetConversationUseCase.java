package com.mudassir.eka.application.conversation;

import com.mudassir.eka.domain.conversation.Conversation;
import com.mudassir.eka.domain.conversation.ConversationId;
import com.mudassir.eka.domain.user.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetConversationUseCase {

    private final ConversationApplicationService conversationService;

    public Conversation execute(ConversationId id, UserId userId) {
        Objects.requireNonNull(id, "conversationId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        return conversationService.getConversation(id, userId);
    }
}
