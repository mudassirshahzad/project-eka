package com.mudassirshahzad.eka.infrastructure.rerank;

import com.mudassirshahzad.eka.domain.chunk.ChunkId;
import com.mudassirshahzad.eka.domain.document.DocumentId;
import com.mudassirshahzad.eka.domain.generation.model.FinishReason;
import com.mudassirshahzad.eka.domain.generation.model.LlmRequest;
import com.mudassirshahzad.eka.domain.generation.model.LlmResponse;
import com.mudassirshahzad.eka.domain.generation.port.LlmPort;
import com.mudassirshahzad.eka.domain.retrieval.model.RetrievedChunk;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmRerankAdapterTest {

    private final TenantId tenantId = TenantId.generate();

    private RetrievedChunk chunk(String content, int rank) {
        return new RetrievedChunk(ChunkId.generate(), DocumentId.generate(), tenantId,
                content, 1.0 - rank * 0.1, rank);
    }

    /** Returns a fixed score per passage content; anything unmatched scores 0. */
    private LlmPort scorer(Map<String, String> scoreByContent) {
        return request -> {
            String userText = request.promptRequest().userText();
            String score = scoreByContent.entrySet().stream()
                    .filter(e -> userText.contains(e.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst().orElse("0.0");
            return new LlmResponse(score, FinishReason.STOP, "stub", 0, 0, 0L);
        };
    }

    @Test
    void reordersCandidatesByModelScore() {
        List<RetrievedChunk> candidates = List.of(chunk("alpha", 0), chunk("bravo", 1), chunk("charlie", 2));
        LlmRerankAdapter adapter = new LlmRerankAdapter(
                scorer(Map.of("alpha", "0.1", "bravo", "0.9", "charlie", "0.5")), 20);

        List<RetrievedChunk> result = adapter.rerank("q", candidates, 3);

        assertThat(result).extracting(RetrievedChunk::content)
                .containsExactly("bravo", "charlie", "alpha");
    }

    @Test
    void rewritesRankToTheNewPosition() {
        List<RetrievedChunk> candidates = List.of(chunk("alpha", 0), chunk("bravo", 1));
        LlmRerankAdapter adapter = new LlmRerankAdapter(scorer(Map.of("alpha", "0.2", "bravo", "0.8")), 20);

        assertThat(adapter.rerank("q", candidates, 2)).extracting(RetrievedChunk::rank)
                .containsExactly(0, 1);
    }

    @Test
    void neverReturnsMoreThanTopN() {
        List<RetrievedChunk> candidates = List.of(chunk("a", 0), chunk("b", 1), chunk("c", 2), chunk("d", 3));
        LlmRerankAdapter adapter = new LlmRerankAdapter(scorer(Map.of()), 20);

        assertThat(adapter.rerank("q", candidates, 2)).hasSize(2);
    }

    @Test
    void emptyOrNullCandidates_returnEmptyWithoutCallingTheModel() {
        AtomicInteger calls = new AtomicInteger();
        LlmPort counting = request -> {
            calls.incrementAndGet();
            return new LlmResponse("1.0", FinishReason.STOP, "stub", 0, 0, 0L);
        };
        LlmRerankAdapter adapter = new LlmRerankAdapter(counting, 20);

        assertThat(adapter.rerank("q", List.of(), 5)).isEmpty();
        assertThat(adapter.rerank("q", null, 5)).isEmpty();
        assertThat(adapter.rerank("q", List.of(chunk("a", 0)), 0)).isEmpty();
        assertThat(calls).hasValue(0);
    }

    @Test
    void modelFailure_preservesTheIncomingOrderInsteadOfThrowing() {
        List<RetrievedChunk> candidates = List.of(chunk("first", 0), chunk("second", 1), chunk("third", 2));
        LlmRerankAdapter adapter = new LlmRerankAdapter(request -> {
            throw new IllegalStateException("model down");
        }, 20);

        // The RerankPort contract: degrade to the input ordering, never fail the retrieval.
        assertThat(adapter.rerank("q", candidates, 3)).extracting(RetrievedChunk::content)
                .containsExactly("first", "second", "third");
    }

    @Test
    void unparseableOrOutOfRangeScores_fallBackToTheIncomingPosition() {
        List<RetrievedChunk> candidates = List.of(chunk("first", 0), chunk("second", 1), chunk("third", 2));

        // "not a number", an out-of-range value, and a blank reply must all be ignored rather than
        // treated as a score — otherwise malformed output would silently reshuffle results.
        LlmRerankAdapter adapter = new LlmRerankAdapter(
                scorer(Map.of("first", "banana", "second", "7.5", "third", "")), 20);

        assertThat(adapter.rerank("q", candidates, 3)).extracting(RetrievedChunk::content)
                .containsExactly("first", "second", "third");
    }

    @Test
    void parsesAScoreWithTrailingText() {
        List<RetrievedChunk> candidates = List.of(chunk("low", 0), chunk("high", 1));
        LlmRerankAdapter adapter = new LlmRerankAdapter(
                scorer(Map.of("low", "0.10", "high", "0.95 — highly relevant")), 20);

        assertThat(adapter.rerank("q", candidates, 2)).extracting(RetrievedChunk::content)
                .containsExactly("high", "low");
    }

    @Test
    void boundsTheNumberOfModelCallsByMaxCandidates() {
        List<RetrievedChunk> candidates = new ArrayList<>();
        for (int i = 0; i < 10; i++) candidates.add(chunk("c" + i, i));

        AtomicInteger calls = new AtomicInteger();
        LlmPort counting = request -> {
            calls.incrementAndGet();
            return new LlmResponse("0.5", FinishReason.STOP, "stub", 0, 0, 0L);
        };

        new LlmRerankAdapter(counting, 3).rerank("q", candidates, 10);

        // One model call per scored candidate is the cost model; max-candidates is what bounds it.
        assertThat(calls).hasValue(3);
    }

    @Test
    void tiedScoresKeepTheIncomingOrder() {
        List<RetrievedChunk> candidates = List.of(chunk("a", 0), chunk("b", 1), chunk("c", 2));
        LlmRerankAdapter adapter = new LlmRerankAdapter(
                scorer(Map.of("a", "0.5", "b", "0.5", "c", "0.5")), 20);

        assertThat(adapter.rerank("q", candidates, 3)).extracting(RetrievedChunk::content)
                .containsExactly("a", "b", "c");
    }

    @Test
    void scoringPromptCarriesBothQueryAndPassage() {
        List<String> seen = new ArrayList<>();
        LlmPort capturing = request -> {
            seen.add(request.promptRequest().userText());
            return new LlmResponse("0.5", FinishReason.STOP, "stub", 0, 0, 0L);
        };

        new LlmRerankAdapter(capturing, 20).rerank("what is the refund window?",
                List.of(chunk("Refunds within 30 days.", 0)), 1);

        assertThat(seen).hasSize(1);
        assertThat(seen.getFirst()).contains("what is the refund window?")
                                   .contains("Refunds within 30 days.");
    }

    @Test
    void constructorRejectsInvalidConfiguration() {
        LlmPort any = request -> new LlmResponse("0.5", FinishReason.STOP, "stub", 0, 0, 0L);

        assertThatThrownBy(() -> new LlmRerankAdapter(any, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max-candidates");
        assertThatThrownBy(() -> new LlmRerankAdapter(null, 20))
                .isInstanceOf(NullPointerException.class);
    }
}
