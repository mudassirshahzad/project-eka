package com.mudassir.eka.application.conversation;

import com.mudassir.eka.application.chat.ChatSessionApplicationService;
import com.mudassir.eka.application.shared.ApplicationException;
import com.mudassir.eka.domain.conversation.ChatSession;
import com.mudassir.eka.domain.conversation.ConversationId;
import com.mudassir.eka.domain.user.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteConversationUseCaseTest {

    @Mock  private ConversationApplicationService conversationService;
    @Mock  private ChatSessionApplicationService  chatSessionService;
    @InjectMocks private DeleteConversationUseCase useCase;

    private final ConversationId conversationId = ConversationId.generate();
    private final UserId         userId         = UserId.generate();

    @Test
    void execute_rejectsNullConversationId() {
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(null, userId));
    }

    @Test
    void execute_rejectsNullUserId() {
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(conversationId, null));
    }

    @Test
    void execute_rejectsDeleteWhenActiveSessionExists() {
        ChatSession activeSession = mock(ChatSession.class);
        when(chatSessionService.findActiveSession(conversationId)).thenReturn(Optional.of(activeSession));

        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> useCase.execute(conversationId, userId))
                .withMessageContaining("active session");

        verify(conversationService, never()).deleteConversation(conversationId, userId);
    }

    @Test
    void execute_delegatesWhenNoActiveSession() {
        when(chatSessionService.findActiveSession(conversationId)).thenReturn(Optional.empty());

        useCase.execute(conversationId, userId);

        verify(conversationService).deleteConversation(conversationId, userId);
    }
}
