package com.mudassir.eka.application.conversation;

import com.mudassir.eka.domain.conversation.Conversation;
import com.mudassir.eka.domain.shared.PageRequest;
import com.mudassir.eka.domain.shared.PageResult;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListConversationsUseCase {

    private final ConversationApplicationService conversationService;

    public PageResult<Conversation> execute(UserId userId, TenantId tenantId,
                                            PageRequest pageRequest) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        return conversationService.listConversations(userId, tenantId, pageRequest);
    }
}
