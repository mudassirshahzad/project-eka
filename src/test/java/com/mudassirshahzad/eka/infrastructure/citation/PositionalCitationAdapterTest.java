package com.mudassirshahzad.eka.infrastructure.citation;

import com.mudassirshahzad.eka.domain.chunk.ChunkId;
import com.mudassirshahzad.eka.domain.conversation.Citation;
import com.mudassirshahzad.eka.domain.document.DocumentId;
import com.mudassirshahzad.eka.domain.retrieval.model.AssembledChunk;
import com.mudassirshahzad.eka.domain.retrieval.model.AssembledContext;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PositionalCitationAdapterTest {

    private static final int BUDGET = 4096;

    private final PositionalCitationAdapter adapter  = new PositionalCitationAdapter();
    private final TenantId                  tenantId = TenantId.generate();
    private final DocumentId                docId    = DocumentId.generate();

    // ── Null guards ───────────────────────────────────────────────────────────

    @Test
    void resolve_throwsOnNullGeneratedText() {
        AssembledContext ctx = context(chunk("content", 0, 0.9));

        assertThatNullPointerException()
                .isThrownBy(() -> adapter.resolve(null, ctx, tenantId))
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
        AssembledContext ctx = context(chunk("content", 0, 0.9));

        assertThatNullPointerException()
                .isThrownBy(() -> adapter.resolve("text", ctx, null))
                .withMessageContaining("tenantId");
    }

    // ── Single citation ───────────────────────────────────────────────────────

    @Test
    void resolve_singleMarker_resolvesToMatchingChunk() {
        AssembledChunk chunk = chunk("RAG content", 0, 0.87);
        AssembledContext ctx = context(chunk);

        List<Citation> citations = adapter.resolve("The answer is [SOURCE:1].", ctx, tenantId);

        assertThat(citations).containsExactly(Citation.of(chunk.chunkId(), chunk.score()));
    }

    @Test
    void resolve_citationScoreMatchesChunkScore() {
        AssembledChunk chunk = chunk("content", 0, 0.42);
        AssembledContext ctx = context(chunk);

        List<Citation> citations = adapter.resolve("[SOURCE:1]", ctx, tenantId);

        assertThat(citations.get(0).relevanceScore()).isEqualTo(0.42);
    }

    // ── Multiple citations / ordering ─────────────────────────────────────────

    @Test
    void resolve_multipleMarkers_resolvesAllInFirstAppearanceOrder() {
        AssembledChunk c0 = chunk("first", 0, 0.9);
        AssembledChunk c1 = chunk("second", 1, 0.8);
        AssembledChunk c2 = chunk("third", 2, 0.7);
        AssembledContext ctx = context(c0, c1, c2);

        List<Citation> citations = adapter.resolve(
                "See [SOURCE:1], [SOURCE:2] and [SOURCE:3].", ctx, tenantId);

        assertThat(citations).extracting(Citation::chunkId)
                .containsExactly(c0.chunkId(), c1.chunkId(), c2.chunkId());
    }

    @Test
    void resolve_markersOutOfNumericOrder_preservesTextAppearanceOrder() {
        AssembledChunk c0 = chunk("first", 0, 0.9);
        AssembledChunk c1 = chunk("second", 1, 0.8);
        AssembledContext ctx = context(c0, c1);

        List<Citation> citations = adapter.resolve(
                "First [SOURCE:2], then earlier [SOURCE:1].", ctx, tenantId);

        assertThat(citations).extracting(Citation::chunkId)
                .containsExactly(c1.chunkId(), c0.chunkId());
    }

    // ── Duplicate / repeated markers ──────────────────────────────────────────

    @Test
    void resolve_duplicateMarker_dedupesToSingleCitation() {
        AssembledChunk chunk = chunk("content", 0, 0.9);
        AssembledContext ctx = context(chunk);

        List<Citation> citations = adapter.resolve(
                "[SOURCE:1] repeats the same idea as [SOURCE:1] again.", ctx, tenantId);

        assertThat(citations).containsExactly(Citation.of(chunk.chunkId(), chunk.score()));
    }

    @Test
    void resolve_repeatedMarkersInArbitraryOrder_keepsFirstOccurrencePosition() {
        AssembledChunk c0 = chunk("first", 0, 0.9);
        AssembledChunk c1 = chunk("second", 1, 0.8);
        AssembledContext ctx = context(c0, c1);

        List<Citation> citations = adapter.resolve(
                "[SOURCE:2] [SOURCE:1] [SOURCE:2] [SOURCE:1]", ctx, tenantId);

        assertThat(citations).extracting(Citation::chunkId)
                .containsExactly(c1.chunkId(), c0.chunkId());
    }

    // ── Missing / out-of-range references ─────────────────────────────────────

    @Test
    void resolve_markerReferencingUnknownChunk_ignoredSafely() {
        AssembledContext ctx = context(chunk("only chunk", 0, 0.9));

        List<Citation> citations = adapter.resolve("Says [SOURCE:5].", ctx, tenantId);

        assertThat(citations).isEmpty();
    }

    @Test
    void resolve_zeroIndex_ignoredAsOutOfRange() {
        AssembledContext ctx = context(chunk("content", 0, 0.9));

        List<Citation> citations = adapter.resolve("Says [SOURCE:0].", ctx, tenantId);

        assertThat(citations).isEmpty();
    }

    @Test
    void resolve_validAndInvalidMarkersMixed_resolvesOnlyTheValidOne() {
        AssembledChunk chunk = chunk("content", 0, 0.9);
        AssembledContext ctx = context(chunk);

        List<Citation> citations = adapter.resolve(
                "Says [SOURCE:1] and also [SOURCE:99].", ctx, tenantId);

        assertThat(citations).containsExactly(Citation.of(chunk.chunkId(), chunk.score()));
    }

    // ── Malformed markers ──────────────────────────────────────────────────────

    @Test
    void resolve_nonDigitMarker_ignored() {
        AssembledContext ctx = context(chunk("content", 0, 0.9));

        List<Citation> citations = adapter.resolve("Says [SOURCE:abc].", ctx, tenantId);

        assertThat(citations).isEmpty();
    }

    @Test
    void resolve_emptyMarkerIndex_ignored() {
        AssembledContext ctx = context(chunk("content", 0, 0.9));

        List<Citation> citations = adapter.resolve("Says [SOURCE:].", ctx, tenantId);

        assertThat(citations).isEmpty();
    }

    @Test
    void resolve_markerMissingClosingBracket_ignored() {
        AssembledContext ctx = context(chunk("content", 0, 0.9));

        List<Citation> citations = adapter.resolve("Says [SOURCE:1 with no bracket", ctx, tenantId);

        assertThat(citations).isEmpty();
    }

    @Test
    void resolve_markerWithWhitespaceInsideBrackets_ignored() {
        AssembledContext ctx = context(chunk("content", 0, 0.9));

        List<Citation> citations = adapter.resolve("Says [SOURCE: 1].", ctx, tenantId);

        assertThat(citations).isEmpty();
    }

    @Test
    void resolve_negativeSignedMarker_ignored() {
        AssembledContext ctx = context(chunk("content", 0, 0.9));

        List<Citation> citations = adapter.resolve("Says [SOURCE:-1].", ctx, tenantId);

        assertThat(citations).isEmpty();
    }

    @Test
    void resolve_numericOverflowMarker_ignoredWithoutThrowing() {
        AssembledContext ctx = context(chunk("content", 0, 0.9));

        List<Citation> citations = adapter.resolve(
                "Says [SOURCE:99999999999999999999999].", ctx, tenantId);

        assertThat(citations).isEmpty();
    }

    @Test
    void resolve_malformedMarkerFollowedByValidMarker_resolvesTheValidOne() {
        AssembledChunk chunk = chunk("content", 0, 0.9);
        AssembledContext ctx = context(chunk);

        List<Citation> citations = adapter.resolve(
                "Bad ref [SOURCE:abc] then good ref [SOURCE:1].", ctx, tenantId);

        assertThat(citations).containsExactly(Citation.of(chunk.chunkId(), chunk.score()));
    }

    @Test
    void resolve_adjacentMarkersWithNoSeparator_bothResolve() {
        AssembledChunk c0 = chunk("first", 0, 0.9);
        AssembledChunk c1 = chunk("second", 1, 0.8);
        AssembledContext ctx = context(c0, c1);

        List<Citation> citations = adapter.resolve("[SOURCE:1][SOURCE:2]", ctx, tenantId);

        assertThat(citations).extracting(Citation::chunkId)
                .containsExactly(c0.chunkId(), c1.chunkId());
    }

    // ── No citations ──────────────────────────────────────────────────────────

    @Test
    void resolve_noMarkersInText_returnsEmptyList() {
        AssembledContext ctx = context(chunk("content", 0, 0.9));

        List<Citation> citations = adapter.resolve("A plain answer with no citations.", ctx, tenantId);

        assertThat(citations).isEmpty();
    }

    @Test
    void resolve_emptyGeneratedText_returnsEmptyList() {
        AssembledContext ctx = context(chunk("content", 0, 0.9));

        List<Citation> citations = adapter.resolve("", ctx, tenantId);

        assertThat(citations).isEmpty();
    }

    @Test
    void resolve_emptyContext_returnsEmptyListEvenWhenMarkersPresent() {
        AssembledContext emptyCtx = new AssembledContext(List.of(), "query", BUDGET, 0);

        List<Citation> citations = adapter.resolve("Says [SOURCE:1].", emptyCtx, tenantId);

        assertThat(citations).isEmpty();
    }

    // ── Determinism ───────────────────────────────────────────────────────────

    @Test
    void resolve_sameInputs_produceIdenticalOutput() {
        AssembledChunk c0 = chunk("first", 0, 0.9);
        AssembledChunk c1 = chunk("second", 1, 0.8);
        AssembledContext ctx = context(c0, c1);
        String text = "See [SOURCE:2] and [SOURCE:1] and [SOURCE:1] again.";

        List<Citation> first  = adapter.resolve(text, ctx, tenantId);
        List<Citation> second = adapter.resolve(text, ctx, tenantId);

        assertThat(first).isEqualTo(second);
    }

    // ── Result immutability ───────────────────────────────────────────────────

    @Test
    void resolve_resultIsUnmodifiable() {
        AssembledContext ctx = context(chunk("content", 0, 0.9));
        List<Citation> citations = adapter.resolve("[SOURCE:1]", ctx, tenantId);

        assertThatThrownBy(() -> citations.add(Citation.of(ChunkId.generate(), 0.5)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AssembledContext context(AssembledChunk... chunks) {
        int estimated = chunks.length * 10;
        return new AssembledContext(List.of(chunks), "query", BUDGET, estimated);
    }

    private AssembledChunk chunk(String content, int position, double score) {
        return new AssembledChunk(ChunkId.generate(), docId, tenantId, content, score, position);
    }
}
