package com.mudassirshahzad.eka.application.conversation;

import com.mudassirshahzad.eka.domain.conversation.Conversation;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * v0.6.1, ADR EX08: title-invariant tests (blank/oversized) moved to
 * {@code domain.conversation.ConversationTest} — that validation now lives in
 * {@link Conversation#create}, not here. This class covers only what remains this use case's own
 * responsibility: identity null-guards and delegation.
 */
@ExtendWith(MockitoExtension.class)
class CreateConversationUseCaseTest {

    @Mock  private ConversationApplicationService conversationService;
    @InjectMocks private CreateConversationUseCase useCase;

    private final UserId   userId   = UserId.generate();
    private final TenantId tenantId = TenantId.generate();

    @Test
    void execute_rejectsNullCommand() {
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(null));
    }

    @Test
    void execute_delegatesWhenValid() {
        var cmd = new CreateConversationCommand(userId, tenantId, "Project Alpha");
        Conversation saved = Conversation.create(userId, tenantId, "Project Alpha");
        when(conversationService.createConversation(cmd)).thenReturn(saved);

        Conversation result = useCase.execute(cmd);

        assertThat(result).isSameAs(saved);
        verify(conversationService).createConversation(cmd);
    }
}
