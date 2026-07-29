package com.mudassirshahzad.eka.infrastructure.guardrails;

import com.mudassirshahzad.eka.domain.generation.model.GuardrailResult;
import com.mudassirshahzad.eka.domain.generation.model.GuardrailStatus;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class PassthroughOutputGuardrailsAdapterTest {

    private final PassthroughOutputGuardrailsAdapter adapter = new PassthroughOutputGuardrailsAdapter();
    private final TenantId tenantId = TenantId.generate();

    // ── Null guards ───────────────────────────────────────────────────────────

    @Test
    void apply_throwsOnNullText() {
        assertThatNullPointerException()
                .isThrownBy(() -> adapter.apply(null, tenantId))
                .withMessageContaining("generatedText");
    }

    @Test
    void apply_throwsOnNullTenantId() {
        assertThatNullPointerException()
                .isThrownBy(() -> adapter.apply("text", null))
                .withMessageContaining("tenantId");
    }

    // ── Always passes ─────────────────────────────────────────────────────────

    @Test
    void apply_alwaysReturnsPASS() {
        GuardrailResult result = adapter.apply("Any generated text.", tenantId);
        assertThat(result.status()).isEqualTo(GuardrailStatus.PASS);
        assertThat(result.isPassed()).isTrue();
    }

    @Test
    void apply_returnsTextUnchanged() {
        String text = "RAG combines retrieval and generation for grounded answers.";
        GuardrailResult result = adapter.apply(text, tenantId);
        assertThat(result.text()).isEqualTo(text);
    }

    @Test
    void apply_acceptsEmptyText() {
        GuardrailResult result = adapter.apply("", tenantId);
        assertThat(result.isPassed()).isTrue();
        assertThat(result.text()).isEmpty();
    }
}
