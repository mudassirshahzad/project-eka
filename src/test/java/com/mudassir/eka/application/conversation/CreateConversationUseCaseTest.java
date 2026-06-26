package com.mudassir.eka.application.conversation;

import com.mudassir.eka.application.shared.ApplicationException;
import com.mudassir.eka.domain.conversation.Conversation;
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
    void execute_rejectsNullTitle() {
        var cmd = new CreateConversationCommand(userId, tenantId, null);
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> useCase.execute(cmd))
                .withMessageContaining("title");
    }

    @Test
    void execute_rejectsBlankTitle() {
        var cmd = new CreateConversationCommand(userId, tenantId, "   ");
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> useCase.execute(cmd))
                .withMessageContaining("title");
    }

    @Test
    void execute_rejectsTitleExceedingMaxLength() {
        String longTitle = "A".repeat(501);
        var cmd = new CreateConversationCommand(userId, tenantId, longTitle);
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> useCase.execute(cmd))
                .withMessageContaining("500");
    }

    @Test
    void execute_acceptsTitleAtMaxLength() {
        String maxTitle = "A".repeat(500);
        var cmd = new CreateConversationCommand(userId, tenantId, maxTitle);
        Conversation saved = Conversation.create(userId, tenantId, maxTitle);
        when(conversationService.createConversation(cmd)).thenReturn(saved);

        Conversation result = useCase.execute(cmd);

        assertThat(result).isSameAs(saved);
        verify(conversationService).createConversation(cmd);
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
