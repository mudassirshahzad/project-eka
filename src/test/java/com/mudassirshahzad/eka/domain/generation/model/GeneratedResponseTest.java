package com.mudassirshahzad.eka.domain.generation.model;

import com.mudassirshahzad.eka.domain.chunk.ChunkId;
import com.mudassirshahzad.eka.domain.conversation.Citation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneratedResponseTest {

    private final Citation citation = Citation.of(ChunkId.generate(), 0.9);

    // ── Null guards ───────────────────────────────────────────────────────────

    @Test
    void constructor_rejectsNullGeneratedText() {
        assertThatNullPointerException()
                .isThrownBy(() -> new GeneratedResponse(null, List.of(), "qwen3", 100, 300L))
                .withMessageContaining("generatedText");
    }

    @Test
    void constructor_rejectsNullCitations() {
        assertThatNullPointerException()
                .isThrownBy(() -> new GeneratedResponse("text", null, "qwen3", 100, 300L))
                .withMessageContaining("citations");
    }

    @Test
    void constructor_rejectsNullModelName() {
        assertThatNullPointerException()
                .isThrownBy(() -> new GeneratedResponse("text", List.of(), null, 100, 300L))
                .withMessageContaining("modelName");
    }

    // ── Field preservation ────────────────────────────────────────────────────

    @Test
    void constructor_preservesAllFields() {
        var response = new GeneratedResponse("the answer", List.of(citation), "qwen3", 150, 450L);
        assertThat(response.generatedText()).isEqualTo("the answer");
        assertThat(response.citations()).containsExactly(citation);
        assertThat(response.modelName()).isEqualTo("qwen3");
        assertThat(response.totalTokens()).isEqualTo(150);
        assertThat(response.latencyMs()).isEqualTo(450L);
    }

    // ── Defensive copy ────────────────────────────────────────────────────────

    @Test
    void constructor_defensivelyCopiesCitationsList() {
        List<Citation> mutable = new ArrayList<>(List.of(citation));
        var response = new GeneratedResponse("text", mutable, "qwen3", 100, 200L);
        mutable.clear();
        assertThat(response.citations()).containsExactly(citation);
    }

    @Test
    void citations_areUnmodifiable() {
        var response = new GeneratedResponse("text", List.of(citation), "qwen3", 100, 200L);
        List<Citation> citations = response.citations();
        assertThatThrownBy(() -> citations.add(Citation.of(ChunkId.generate(), 0.5)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── hasCitations ─────────────────────────────────────────────────────────

    @Test
    void hasCitations_returnsTrueWhenCitationsPresent() {
        var response = new GeneratedResponse("text", List.of(citation), "qwen3", 100, 200L);
        assertThat(response.hasCitations()).isTrue();
    }

    @Test
    void hasCitations_returnsFalseWhenEmpty() {
        var response = new GeneratedResponse("text", List.of(), "qwen3", 100, 200L);
        assertThat(response.hasCitations()).isFalse();
    }
}
