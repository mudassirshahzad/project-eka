package com.mudassir.eka.application.chat;

import com.mudassir.eka.application.shared.ApplicationException;
import com.mudassir.eka.domain.conversation.ChatSession;
import com.mudassir.eka.domain.conversation.ConversationId;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StartChatSessionUseCaseTest {

    @Mock  private ChatSessionApplicationService chatSessionService;
    @InjectMocks private StartChatSessionUseCase useCase;

    private final ConversationId conversationId = ConversationId.generate();
    private final UserId         userId         = UserId.generate();
    private final TenantId       tenantId       = TenantId.generate();

    @Test
    void execute_rejectsNullCommand() {
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(null));
    }

    @Test
    void execute_rejectsNullConversationId() {
        var cmd = new StartChatSessionCommand(null, userId, tenantId, "llama3.2");
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(cmd));
    }

    @Test
    void execute_rejectsNullModelId() {
        var cmd = new StartChatSessionCommand(conversationId, userId, tenantId, null);
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> useCase.execute(cmd))
                .withMessageContaining("modelId");
    }

    @Test
    void execute_rejectsBlankModelId() {
        var cmd = new StartChatSessionCommand(conversationId, userId, tenantId, "  ");
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> useCase.execute(cmd))
                .withMessageContaining("modelId");
    }

    @Test
    void execute_delegatesWhenValid() {
        var cmd = new StartChatSessionCommand(conversationId, userId, tenantId, "llama3.2");
        ChatSession session = ChatSession.start(conversationId, userId, tenantId, "llama3.2");
        when(chatSessionService.startSession(cmd)).thenReturn(session);

        ChatSession result = useCase.execute(cmd);

        assertThat(result).isSameAs(session);
        verify(chatSessionService).startSession(cmd);
    }
}
