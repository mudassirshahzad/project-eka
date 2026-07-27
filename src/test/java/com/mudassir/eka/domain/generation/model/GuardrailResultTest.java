package com.mudassir.eka.domain.generation.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class GuardrailResultTest {

    // ── Null guards ───────────────────────────────────────────────────────────

    @Test
    void constructor_rejectsNullStatus() {
        assertThatNullPointerException()
                .isThrownBy(() -> new GuardrailResult(null, "text"))
                .withMessageContaining("status");
    }

    @Test
    void constructor_rejectsNullText() {
        assertThatNullPointerException()
                .isThrownBy(() -> new GuardrailResult(GuardrailStatus.PASS, null))
                .withMessageContaining("text");
    }

    // ── Factory methods ───────────────────────────────────────────────────────

    @Test
    void pass_createsPassResultWithText() {
        GuardrailResult result = GuardrailResult.pass("safe answer");
        assertThat(result.status()).isEqualTo(GuardrailStatus.PASS);
        assertThat(result.text()).isEqualTo("safe answer");
        assertThat(result.isPassed()).isTrue();
        assertThat(result.isBlocked()).isFalse();
    }

    @Test
    void block_createsBlockResultWithFallbackText() {
        GuardrailResult result = GuardrailResult.block("I cannot provide that information.");
        assertThat(result.status()).isEqualTo(GuardrailStatus.BLOCK);
        assertThat(result.text()).isEqualTo("I cannot provide that information.");
        assertThat(result.isPassed()).isFalse();
        assertThat(result.isBlocked()).isTrue();
    }

    // ── Field preservation ────────────────────────────────────────────────────

    @Test
    void constructor_preservesStatusAndText() {
        GuardrailResult result = new GuardrailResult(GuardrailStatus.PASS, "the answer");
        assertThat(result.status()).isEqualTo(GuardrailStatus.PASS);
        assertThat(result.text()).isEqualTo("the answer");
    }

    // ── Empty text is allowed ─────────────────────────────────────────────────

    @Test
    void pass_acceptsEmptyText() {
        GuardrailResult result = GuardrailResult.pass("");
        assertThat(result.text()).isEmpty();
        assertThat(result.isPassed()).isTrue();
    }
}
