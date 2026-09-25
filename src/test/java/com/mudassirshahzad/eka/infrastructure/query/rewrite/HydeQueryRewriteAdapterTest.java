package com.mudassirshahzad.eka.infrastructure.query.rewrite;

import com.mudassirshahzad.eka.domain.shared.TenantId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HydeQueryRewriteAdapterTest {

    @Mock private ChatModel chatModel;

    private final TenantId tenantId = TenantId.generate();
    private static final String QUERY = "what is the refund window?";

    private void modelReturns(String text) {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
    }

    @Test
    void disabled_returnsTheQueryUnchangedWithoutCallingTheModel() {
        HydeQueryRewriteAdapter adapter = new HydeQueryRewriteAdapter(chatModel, false);

        assertThat(adapter.rewrite(QUERY, tenantId)).isEqualTo(QUERY);
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void enabled_retrievesWithTheHypotheticalAnswerAndKeepsTheOriginalQuestion() {
        modelReturns("Refunds are accepted within 30 days of purchase.");
        HydeQueryRewriteAdapter adapter = new HydeQueryRewriteAdapter(chatModel, true);

        String effective = adapter.rewrite(QUERY, tenantId);

        // The hypothetical answer is what closes the question/answer vocabulary gap, but the
        // original question is retained so a strong lexical match on the user's own terms survives.
        assertThat(effective).contains("Refunds are accepted within 30 days of purchase.")
                             .contains(QUERY);
    }

    @Test
    void blankModelOutput_fallsBackToTheOriginalQuery() {
        modelReturns("   ");
        HydeQueryRewriteAdapter adapter = new HydeQueryRewriteAdapter(chatModel, true);

        assertThat(adapter.rewrite(QUERY, tenantId)).isEqualTo(QUERY);
    }

    @Test
    void modelFailure_fallsBackToTheOriginalQueryInsteadOfThrowing() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("ollama down"));
        HydeQueryRewriteAdapter adapter = new HydeQueryRewriteAdapter(chatModel, true);

        // Retrieval must never fail because a query-expansion enhancement did — the same contract
        // OllamaQueryRewriteAdapter already honours.
        assertThat(adapter.rewrite(QUERY, tenantId)).isEqualTo(QUERY);
    }

    @Test
    void blankOrNullQuery_isReturnedUnchangedWithoutCallingTheModel() {
        HydeQueryRewriteAdapter adapter = new HydeQueryRewriteAdapter(chatModel, true);

        assertThat(adapter.rewrite("   ", tenantId)).isEqualTo("   ");
        assertThat(adapter.rewrite(null, tenantId)).isNull();
        verify(chatModel, never()).call(any(Prompt.class));
    }
}
