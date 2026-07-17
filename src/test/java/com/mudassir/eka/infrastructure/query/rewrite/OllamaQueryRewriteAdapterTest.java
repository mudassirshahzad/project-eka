package com.mudassir.eka.infrastructure.query.rewrite;

import com.mudassir.eka.domain.shared.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OllamaQueryRewriteAdapterTest {

    @Mock private ChatModel chatModel;

    private OllamaQueryRewriteAdapter adapter;

    private final TenantId tenantId = TenantId.generate();

    @BeforeEach
    void setUp() {
        adapter = new OllamaQueryRewriteAdapter(chatModel, true);
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    @Test
    void constructor_rejectsNullChatModel() {
        assertThatNullPointerException()
                .isThrownBy(() -> new OllamaQueryRewriteAdapter(null, true))
                .withMessageContaining("chatModel");
    }

    // ── Successful rewrite ────────────────────────────────────────────────────

    @Test
    void rewrite_returnsRewrittenQuery_whenLlmSucceeds() {
        stubRewrite("service level agreement terms and conditions");

        String result = adapter.rewrite("what are the SLA terms?", tenantId);

        assertThat(result).isEqualTo("service level agreement terms and conditions");
    }

    @Test
    void rewrite_stripsLeadingAndTrailingWhitespace_fromLlmResponse() {
        stubRewrite("  non-disclosure agreement obligations  \n");

        String result = adapter.rewrite("what are the NDA obligations?", tenantId);

        assertThat(result).isEqualTo("non-disclosure agreement obligations");
    }

    @Test
    void rewrite_callsChatModel_withSystemAndUserMessages() {
        stubRewrite("rewritten query");

        adapter.rewrite("original query", tenantId);

        verify(chatModel).call(any(Prompt.class));
    }

    // ── Fallback on failure ───────────────────────────────────────────────────

    @Test
    void rewrite_fallsBackToOriginal_whenLlmThrows() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("Ollama connection refused"));

        String result = adapter.rewrite("find contract clauses", tenantId);

        assertThat(result).isEqualTo("find contract clauses");
    }

    @Test
    void rewrite_fallsBackToOriginal_whenLlmReturnsBlank() {
        stubRewrite("   ");

        String result = adapter.rewrite("original query", tenantId);

        assertThat(result).isEqualTo("original query");
    }

    @Test
    void rewrite_fallsBackToOriginal_whenLlmReturnsEmpty() {
        stubRewrite("");

        String result = adapter.rewrite("original query", tenantId);

        assertThat(result).isEqualTo("original query");
    }

    // ── Disabled mode ─────────────────────────────────────────────────────────

    @Test
    void rewrite_returnsOriginalQuery_whenDisabled() {
        OllamaQueryRewriteAdapter disabledAdapter = new OllamaQueryRewriteAdapter(chatModel, false);

        String result = disabledAdapter.rewrite("what is an indemnification clause?", tenantId);

        assertThat(result).isEqualTo("what is an indemnification clause?");
    }

    @Test
    void rewrite_doesNotCallLlm_whenDisabled() {
        OllamaQueryRewriteAdapter disabledAdapter = new OllamaQueryRewriteAdapter(chatModel, false);

        disabledAdapter.rewrite("any query", tenantId);

        verify(chatModel, never()).call(any(Prompt.class));
    }

    // ── Guard conditions ──────────────────────────────────────────────────────

    @Test
    void rewrite_returnsNull_whenQueryIsNull() {
        String result = adapter.rewrite(null, tenantId);

        assertThat(result).isNull();
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void rewrite_returnsBlank_whenQueryIsBlank() {
        String result = adapter.rewrite("   ", tenantId);

        assertThat(result).isEqualTo("   ");
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void rewrite_returnsEmpty_whenQueryIsEmpty() {
        String result = adapter.rewrite("", tenantId);

        assertThat(result).isEqualTo("");
        verify(chatModel, never()).call(any(Prompt.class));
    }

    // ── Already-optimal query ─────────────────────────────────────────────────

    @Test
    void rewrite_returnsLlmOutput_whenLlmEchoesUnchanged() {
        String query = "non-disclosure agreement confidentiality obligations";
        stubRewrite(query);

        String result = adapter.rewrite(query, tenantId);

        assertThat(result).isEqualTo(query);
    }

    // ── System prompt content ─────────────────────────────────────────────────

    @Test
    void systemPrompt_instructsReturnOnlyRewrittenQuery() {
        assertThat(OllamaQueryRewriteAdapter.SYSTEM_PROMPT)
                .contains("Return ONLY the rewritten query text");
    }

    @Test
    void systemPrompt_forbidsAnsweringTheQuestion() {
        assertThat(OllamaQueryRewriteAdapter.SYSTEM_PROMPT)
                .contains("Do not answer the question");
    }

    @Test
    void systemPrompt_instructsAbbreviationExpansion() {
        assertThat(OllamaQueryRewriteAdapter.SYSTEM_PROMPT)
                .contains("Expand abbreviations");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private void stubRewrite(String text) {
        AssistantMessage message = mock(AssistantMessage.class);
        when(message.getText()).thenReturn(text);

        Generation generation = mock(Generation.class);
        when(generation.getOutput()).thenReturn(message);

        ChatResponse response = mock(ChatResponse.class);
        when(response.getResult()).thenReturn(generation);

        when(chatModel.call(any(Prompt.class))).thenReturn(response);
    }
}
