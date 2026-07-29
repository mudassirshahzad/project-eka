package com.mudassirshahzad.eka.domain.conversation;

import com.mudassirshahzad.eka.domain.shared.PageRequest;
import com.mudassirshahzad.eka.domain.shared.PageResult;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;

import java.util.Optional;

public interface ConversationRepository {

    Conversation save(Conversation conversation);

    Optional<Conversation> findById(ConversationId id);

    Optional<Conversation> findByIdAndUserId(ConversationId id, UserId userId);

    PageResult<Conversation> findByUserIdAndTenantId(UserId userId, TenantId tenantId, PageRequest pageRequest);

    void softDelete(ConversationId id);
}
