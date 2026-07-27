package com.mudassir.eka.domain.generation.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class LlmResponseTest {

    // ── Null guards ───────────────────────────────────────────────────────────

    @Test
    void constructor_rejectsNullGeneratedText() {
        assertThatNullPointerException()
                .isThrownBy(() -> new LlmResponse(null, FinishReason.STOP, "qwen3", 100, 50, 300L))
                .withMessageContaining("generatedText");
    }

    @Test
    void constructor_rejectsNullFinishReason() {
        assertThatNullPointerException()
                .isThrownBy(() -> new LlmResponse("text", null, "qwen3", 100, 50, 300L))
                .withMessageContaining("finishReason");
    }

    @Test
    void constructor_rejectsNullModelName() {
        assertThatNullPointerException()
                .isThrownBy(() -> new LlmResponse("text", FinishReason.STOP, null, 100, 50, 300L))
                .withMessageContaining("modelName");
    }

    // ── Field preservation ────────────────────────────────────────────────────

    @Test
    void constructor_preservesAllFields() {
        var response = new LlmResponse("answer", FinishReason.STOP, "qwen3", 120, 80, 450L);
        assertThat(response.generatedText()).isEqualTo("answer");
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(response.modelName()).isEqualTo("qwen3");
        assertThat(response.promptTokens()).isEqualTo(120);
        assertThat(response.completionTokens()).isEqualTo(80);
        assertThat(response.latencyMs()).isEqualTo(450L);
    }

    // ── totalTokens derived method ────────────────────────────────────────────

    @Test
    void totalTokens_isPromptPlusCompletion() {
        var response = new LlmResponse("text", FinishReason.STOP, "qwen3", 100, 50, 300L);
        assertThat(response.totalTokens()).isEqualTo(150);
    }

    @Test
    void totalTokens_isZeroWhenBothZero() {
        var response = new LlmResponse("text", FinishReason.STOP, "qwen3", 0, 0, 100L);
        assertThat(response.totalTokens()).isZero();
    }

    // ── FinishReason variants ─────────────────────────────────────────────────

    @Test
    void constructor_acceptsLengthFinishReason() {
        var response = new LlmResponse("truncated", FinishReason.LENGTH, "qwen3", 100, 2048, 500L);
        assertThat(response.finishReason()).isEqualTo(FinishReason.LENGTH);
    }

    @Test
    void constructor_acceptsToolCallFinishReason() {
        var response = new LlmResponse("{\"tool\":\"search\"}", FinishReason.TOOL_CALL, "qwen3", 50, 30, 200L);
        assertThat(response.finishReason()).isEqualTo(FinishReason.TOOL_CALL);
    }

    // ── Empty generated text is allowed ─────────────────────────────────────

    @Test
    void constructor_acceptsEmptyGeneratedText() {
        var response = new LlmResponse("", FinishReason.CONTENT_FILTER, "qwen3", 50, 0, 100L);
        assertThat(response.generatedText()).isEmpty();
    }
}
