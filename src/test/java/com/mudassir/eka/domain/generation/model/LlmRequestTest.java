package com.mudassir.eka.domain.generation.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class LlmRequestTest {

    private final PromptRequest     promptRequest = new PromptRequest("system", "user", List.of(), List.of());
    private final GenerationOptions options       = GenerationOptions.DEFAULT;

    // ── Null guards ───────────────────────────────────────────────────────────

    @Test
    void constructor_rejectsNullPromptRequest() {
        assertThatNullPointerException()
                .isThrownBy(() -> new LlmRequest(null, options))
                .withMessageContaining("promptRequest");
    }

    @Test
    void constructor_rejectsNullOptions() {
        assertThatNullPointerException()
                .isThrownBy(() -> new LlmRequest(promptRequest, null))
                .withMessageContaining("options");
    }

    // ── Field preservation ────────────────────────────────────────────────────

    @Test
    void constructor_preservesPromptRequest() {
        var request = new LlmRequest(promptRequest, options);
        assertThat(request.promptRequest()).isSameAs(promptRequest);
    }

    @Test
    void constructor_preservesOptions() {
        var request = new LlmRequest(promptRequest, options);
        assertThat(request.options()).isSameAs(options);
    }

    // ── Custom options roundtrip ───────────────────────────────────────────────

    @Test
    void constructor_preservesCustomOptions() {
        var custom = new GenerationOptions(512, 0.9, 0.8, "mistral");
        var request = new LlmRequest(promptRequest, custom);
        assertThat(request.options().maxTokens()).isEqualTo(512);
        assertThat(request.options().temperature()).isEqualTo(0.9);
        assertThat(request.options().modelNameOverride()).isEqualTo("mistral");
    }
}
