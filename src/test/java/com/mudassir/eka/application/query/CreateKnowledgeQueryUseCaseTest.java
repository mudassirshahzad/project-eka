package com.mudassir.eka.application.query;

import com.mudassir.eka.application.shared.ApplicationException;
import com.mudassir.eka.domain.conversation.ConversationId;
import com.mudassir.eka.domain.query.KnowledgeQuery;
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
class CreateKnowledgeQueryUseCaseTest {

    @Mock  private QueryApplicationService queryService;
    @InjectMocks private CreateKnowledgeQueryUseCase useCase;

    private final UserId         userId         = UserId.generate();
    private final TenantId       tenantId       = TenantId.generate();
    private final ConversationId conversationId = ConversationId.generate();

    @Test
    void execute_rejectsNullCommand() {
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(null));
    }

    @Test
    void execute_rejectsNullUserId() {
        var cmd = new SubmitQueryCommand(null, tenantId, conversationId, "What is RAG?", null);
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(cmd));
    }

    @Test
    void execute_rejectsNullQueryText() {
        var cmd = new SubmitQueryCommand(userId, tenantId, conversationId, null, null);
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> useCase.execute(cmd))
                .withMessageContaining("queryText");
    }

    @Test
    void execute_rejectsBlankQueryText() {
        var cmd = new SubmitQueryCommand(userId, tenantId, conversationId, "   ", null);
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> useCase.execute(cmd))
                .withMessageContaining("queryText");
    }

    @Test
    void execute_rejectsQueryTextExceedingMaxLength() {
        String oversized = "Q".repeat(10_001);
        var cmd = new SubmitQueryCommand(userId, tenantId, conversationId, oversized, null);
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> useCase.execute(cmd))
                .withMessageContaining("10000");
    }

    @Test
    void execute_delegatesWhenValid() {
        var cmd = new SubmitQueryCommand(userId, tenantId, conversationId, "What is RAG?", null);
        KnowledgeQuery saved = KnowledgeQuery.create(userId, tenantId, conversationId, "What is RAG?", null);
        when(queryService.submitQuery(cmd)).thenReturn(saved);

        KnowledgeQuery result = useCase.execute(cmd);

        assertThat(result).isSameAs(saved);
        verify(queryService).submitQuery(cmd);
    }
}
