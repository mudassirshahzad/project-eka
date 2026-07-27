package com.mudassir.eka.infrastructure.citation;

import com.mudassir.eka.domain.conversation.Citation;
import com.mudassir.eka.domain.retrieval.model.AssembledContext;
import com.mudassir.eka.domain.shared.TenantId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class PassthroughCitationAdapterTest {

    private final PassthroughCitationAdapter adapter  = new PassthroughCitationAdapter();
    private final TenantId                   tenantId = TenantId.generate();
    private final AssembledContext           context  = new AssembledContext(List.of(), "query", 4096, 0);

    // ── Null guards ───────────────────────────────────────────────────────────

    @Test
    void resolve_throwsOnNullGeneratedText() {
        assertThatNullPointerException()
                .isThrownBy(() -> adapter.resolve(null, context, tenantId))
                .withMessageContaining("generatedText");
    }

    @Test
    void resolve_throwsOnNullContext() {
        assertThatNullPointerException()
                .isThrownBy(() -> adapter.resolve("text", null, tenantId))
                .withMessageContaining("context");
    }

    @Test
    void resolve_throwsOnNullTenantId() {
        assertThatNullPointerException()
                .isThrownBy(() -> adapter.resolve("text", context, null))
                .withMessageContaining("tenantId");
    }

    // ── Always returns empty list ─────────────────────────────────────────────

    @Test
    void resolve_alwaysReturnsEmptyList() {
        List<Citation> citations = adapter.resolve("Answer with [SOURCE:1] reference.", context, tenantId);
        assertThat(citations).isEmpty();
    }

    @Test
    void resolve_returnsEmptyListForTextWithNoMarkers() {
        List<Citation> citations = adapter.resolve("Plain answer with no citations.", context, tenantId);
        assertThat(citations).isEmpty();
    }
}
