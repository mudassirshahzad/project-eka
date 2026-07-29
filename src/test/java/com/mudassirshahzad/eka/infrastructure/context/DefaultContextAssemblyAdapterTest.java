package com.mudassirshahzad.eka.infrastructure.context;

import com.mudassirshahzad.eka.domain.chunk.ChunkId;
import com.mudassirshahzad.eka.domain.document.DocumentId;
import com.mudassirshahzad.eka.domain.retrieval.model.AssembledChunk;
import com.mudassirshahzad.eka.domain.retrieval.model.AssembledContext;
import com.mudassirshahzad.eka.domain.retrieval.model.RetrievedChunk;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.mudassirshahzad.eka.infrastructure.context.DefaultContextAssemblyAdapter.CHARS_PER_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DefaultContextAssemblyAdapterTest {

    private static final int    DEFAULT_MAX_CHUNKS  = 10;
    private static final int    LARGE_TOKEN_BUDGET  = 100_000;
    private static final String QUERY               = "what is retrieval augmented generation?";

    private final TenantId   tenantId = TenantId.generate();
    private final DocumentId docId    = DocumentId.generate();

    private final DefaultContextAssemblyAdapter adapter =
            new DefaultContextAssemblyAdapter(DEFAULT_MAX_CHUNKS);

    // ── Constructor validation ────────────────────────────────────────────────

    @Test
    void constructor_rejectsZeroMaxChunks() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DefaultContextAssemblyAdapter(0))
                .withMessageContaining("maxChunks must be >= 1");
    }

    @Test
    void constructor_rejectsNegativeMaxChunks() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DefaultContextAssemblyAdapter(-5))
                .withMessageContaining("maxChunks must be >= 1");
    }

    @Test
    void constructor_acceptsMaxChunksOfOne() {
        var singleChunkAdapter = new DefaultContextAssemblyAdapter(1);
        AssembledContext ctx = singleChunkAdapter.assemble(
                List.of(chunk("hello")), QUERY, LARGE_TOKEN_BUDGET);
        assertThat(ctx.size()).isEqualTo(1);
    }

    // ── Null / invalid argument guards ────────────────────────────────────────

    @Test
    void assemble_throwsOnNullChunks() {
        assertThatNullPointerException()
                .isThrownBy(() -> adapter.assemble(null, QUERY, LARGE_TOKEN_BUDGET))
                .withMessageContaining("chunks must not be null");
    }

    @Test
    void assemble_throwsOnNullQueryText() {
        assertThatNullPointerException()
                .isThrownBy(() -> adapter.assemble(List.of(), null, LARGE_TOKEN_BUDGET))
                .withMessageContaining("queryText must not be null");
    }

    @Test
    void assemble_throwsOnNegativeTokenBudget() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> adapter.assemble(List.of(), QUERY, -1))
                .withMessageContaining("tokenBudget must be >= 0");
    }

    // ── Empty / zero budget edge cases ────────────────────────────────────────

    @Test
    void assemble_returnsEmptyContext_forEmptyInput() {
        AssembledContext ctx = adapter.assemble(List.of(), QUERY, LARGE_TOKEN_BUDGET);

        assertThat(ctx.isEmpty()).isTrue();
        assertThat(ctx.queryText()).isEqualTo(QUERY);
        assertThat(ctx.tokenBudget()).isEqualTo(LARGE_TOKEN_BUDGET);
        assertThat(ctx.estimatedTokens()).isZero();
    }

    @Test
    void assemble_returnsEmptyContext_forZeroTokenBudget() {
        AssembledContext ctx = adapter.assemble(
                List.of(chunk("any content")), QUERY, 0);

        assertThat(ctx.isEmpty()).isTrue();
        assertThat(ctx.estimatedTokens()).isZero();
    }

    // ── Ordering ──────────────────────────────────────────────────────────────

    @Test
    void assemble_preservesInputOrderAsPositions() {
        RetrievedChunk c0 = chunk("first chunk content");
        RetrievedChunk c1 = chunk("second chunk content");
        RetrievedChunk c2 = chunk("third chunk content");

        AssembledContext ctx = adapter.assemble(List.of(c0, c1, c2), QUERY, LARGE_TOKEN_BUDGET);

        assertThat(ctx.size()).isEqualTo(3);
        assertThat(ctx.chunks().get(0).position()).isZero();
        assertThat(ctx.chunks().get(1).position()).isEqualTo(1);
        assertThat(ctx.chunks().get(2).position()).isEqualTo(2);
    }

    @Test
    void assemble_positionsAreSequentialAfterDeduplication() {
        ChunkId sharedId = ChunkId.generate();
        RetrievedChunk c0 = chunk(sharedId, "first");
        RetrievedChunk c1 = chunk("second");
        RetrievedChunk c2 = chunk(sharedId, "duplicate of first");  // must be skipped
        RetrievedChunk c3 = chunk("third");

        AssembledContext ctx = adapter.assemble(
                List.of(c0, c1, c2, c3), QUERY, LARGE_TOKEN_BUDGET);

        assertThat(ctx.size()).isEqualTo(3);
        assertThat(ctx.chunks().get(0).position()).isZero();
        assertThat(ctx.chunks().get(1).position()).isEqualTo(1);
        assertThat(ctx.chunks().get(2).position()).isEqualTo(2);
    }

    // ── Deduplication ─────────────────────────────────────────────────────────

    @Test
    void assemble_removesDuplicateChunkIds_keepingFirstOccurrence() {
        ChunkId sharedId = ChunkId.generate();
        RetrievedChunk first  = chunk(sharedId, "original content");
        RetrievedChunk second = chunk(sharedId, "different content for same chunk");

        AssembledContext ctx = adapter.assemble(
                List.of(first, second), QUERY, LARGE_TOKEN_BUDGET);

        assertThat(ctx.size()).isEqualTo(1);
        assertThat(ctx.chunks().get(0).content()).isEqualTo("original content");
        assertThat(ctx.chunks().get(0).chunkId()).isEqualTo(sharedId);
    }

    @Test
    void assemble_handlesAllDuplicates() {
        ChunkId id = ChunkId.generate();
        List<RetrievedChunk> allDuplicates = List.of(
                chunk(id, "a"), chunk(id, "b"), chunk(id, "c"));

        AssembledContext ctx = adapter.assemble(allDuplicates, QUERY, LARGE_TOKEN_BUDGET);

        assertThat(ctx.size()).isEqualTo(1);
        assertThat(ctx.chunks().get(0).content()).isEqualTo("a");
    }

    // ── Max chunks limit ──────────────────────────────────────────────────────

    @Test
    void assemble_respectsMaxChunksLimit() {
        var limitedAdapter = new DefaultContextAssemblyAdapter(3);
        List<RetrievedChunk> chunks = List.of(
                chunk("a"), chunk("b"), chunk("c"), chunk("d"), chunk("e"));

        AssembledContext ctx = limitedAdapter.assemble(chunks, QUERY, LARGE_TOKEN_BUDGET);

        assertThat(ctx.size()).isEqualTo(3);
        assertThat(ctx.chunks().get(0).content()).isEqualTo("a");
        assertThat(ctx.chunks().get(1).content()).isEqualTo("b");
        assertThat(ctx.chunks().get(2).content()).isEqualTo("c");
    }

    @Test
    void assemble_maxChunksOfOneIncludesOnlyFirstChunk() {
        var singleAdapter = new DefaultContextAssemblyAdapter(1);
        List<RetrievedChunk> chunks = List.of(chunk("winner"), chunk("excluded"));

        AssembledContext ctx = singleAdapter.assemble(chunks, QUERY, LARGE_TOKEN_BUDGET);

        assertThat(ctx.size()).isEqualTo(1);
        assertThat(ctx.chunks().get(0).content()).isEqualTo("winner");
    }

    // ── Token budget ──────────────────────────────────────────────────────────

    @Test
    void assemble_stopsAtFirstChunkExceedingBudget() {
        // chunk A: 40 chars = 10 tokens; cumulative = 10
        // chunk B: 80 chars = 20 tokens; cumulative would be 30 — exceeds budget of 25 → STOP
        // chunk C: 8 chars  =  2 tokens; never reached
        RetrievedChunk a = chunk("a".repeat(40));
        RetrievedChunk b = chunk("b".repeat(80));
        RetrievedChunk c = chunk("c".repeat(8));

        AssembledContext ctx = adapter.assemble(List.of(a, b, c), QUERY, 25);

        assertThat(ctx.size()).isEqualTo(1);
        assertThat(ctx.chunks().get(0).content()).isEqualTo("a".repeat(40));
        assertThat(ctx.estimatedTokens()).isEqualTo(10);
    }

    @Test
    void assemble_includesChunkExactlyAtBudgetLimit() {
        // 40 chars = 10 tokens; budget = 10 → exactly fits
        RetrievedChunk a = chunk("a".repeat(40));
        // 4 chars = 1 token; cumulative would be 11 → exceeds budget
        RetrievedChunk b = chunk("bbbb");

        AssembledContext ctx = adapter.assemble(List.of(a, b), QUERY, 10);

        assertThat(ctx.size()).isEqualTo(1);
        assertThat(ctx.estimatedTokens()).isEqualTo(10);
    }

    @Test
    void assemble_returnsEmptyWhenFirstChunkExceedsBudget() {
        // 100 chars = 25 tokens; budget = 10 → even first chunk doesn't fit
        RetrievedChunk big = chunk("x".repeat(100));

        AssembledContext ctx = adapter.assemble(List.of(big), QUERY, 10);

        assertThat(ctx.isEmpty()).isTrue();
        assertThat(ctx.estimatedTokens()).isZero();
    }

    @Test
    void assemble_accumulatesTokensAcrossMultipleChunks() {
        // Each chunk is 40 chars = 10 tokens; 3 chunks = 30 tokens; budget = 35 → 3 fit
        // 4th chunk would be 40 tokens → exceeds budget
        RetrievedChunk c0 = chunk("a".repeat(40));
        RetrievedChunk c1 = chunk("b".repeat(40));
        RetrievedChunk c2 = chunk("c".repeat(40));
        RetrievedChunk c3 = chunk("d".repeat(40));

        AssembledContext ctx = adapter.assemble(List.of(c0, c1, c2, c3), QUERY, 35);

        assertThat(ctx.size()).isEqualTo(3);
        assertThat(ctx.estimatedTokens()).isEqualTo(30);
    }

    // ── Metadata preservation ─────────────────────────────────────────────────

    @Test
    void assemble_preservesAllChunkMetadata() {
        ChunkId    expectedChunkId = ChunkId.generate();
        DocumentId expectedDocId   = DocumentId.generate();
        TenantId   expectedTenant  = TenantId.generate();
        String     expectedContent = "specific content for identity check";
        double     expectedScore   = 0.87;

        RetrievedChunk source = new RetrievedChunk(
                expectedChunkId, expectedDocId, expectedTenant,
                expectedContent, expectedScore, 0);

        AssembledContext ctx = adapter.assemble(List.of(source), QUERY, LARGE_TOKEN_BUDGET);

        assertThat(ctx.size()).isEqualTo(1);
        AssembledChunk assembled = ctx.chunks().get(0);
        assertThat(assembled.chunkId()).isEqualTo(expectedChunkId);
        assertThat(assembled.documentId()).isEqualTo(expectedDocId);
        assertThat(assembled.tenantId()).isEqualTo(expectedTenant);
        assertThat(assembled.content()).isEqualTo(expectedContent);
        assertThat(assembled.score()).isEqualTo(expectedScore);
        assertThat(assembled.position()).isZero();
    }

    @Test
    void assemble_preservesQueryTextInResult() {
        String query = "distinct query text for preservation test";
        AssembledContext ctx = adapter.assemble(List.of(chunk("content")), query, LARGE_TOKEN_BUDGET);

        assertThat(ctx.queryText()).isEqualTo(query);
    }

    @Test
    void assemble_preservesTokenBudgetInResult() {
        int budget = 4096;
        AssembledContext ctx = adapter.assemble(List.of(chunk("content")), QUERY, budget);

        assertThat(ctx.tokenBudget()).isEqualTo(budget);
    }

    // ── Token estimation ──────────────────────────────────────────────────────

    @Test
    void estimateTokens_returnsZeroForEmptyString() {
        assertThat(DefaultContextAssemblyAdapter.estimateTokens("")).isZero();
    }

    @Test
    void estimateTokens_returnsZeroForNull() {
        assertThat(DefaultContextAssemblyAdapter.estimateTokens(null)).isZero();
    }

    @Test
    void estimateTokens_returnsMinimumOfOneForShortString() {
        // "a" = 1 char → 1/4 = 0 → clamped to 1
        assertThat(DefaultContextAssemblyAdapter.estimateTokens("a")).isEqualTo(1);
    }

    @Test
    void estimateTokens_usesCharsPerTokenHeuristic() {
        String text = "x".repeat(CHARS_PER_TOKEN * 20);
        assertThat(DefaultContextAssemblyAdapter.estimateTokens(text)).isEqualTo(20);
    }

    @Test
    void assemble_estimatedTokensReflectsActualIncludedChunks() {
        // 3 chunks at 40 chars each = 10 tokens each = 30 total
        AssembledContext ctx = adapter.assemble(
                List.of(chunk("a".repeat(40)), chunk("b".repeat(40)), chunk("c".repeat(40))),
                QUERY, LARGE_TOKEN_BUDGET);

        assertThat(ctx.estimatedTokens()).isEqualTo(30);
    }

    // ── Immutability ──────────────────────────────────────────────────────────

    @Test
    void assemble_returnsImmutableChunksList() {
        AssembledContext ctx = adapter.assemble(
                List.of(chunk("content")), QUERY, LARGE_TOKEN_BUDGET);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> {
                    try {
                        ctx.chunks().add(null);
                    } catch (UnsupportedOperationException e) {
                        throw new IllegalArgumentException("list is unmodifiable", e);
                    }
                });
    }

    // ── Combined constraints ───────────────────────────────────────────────────

    @Test
    void assemble_appliesDeduplicationBeforeCountingAgainstMaxChunks() {
        // With maxChunks=3 and one duplicate: 4 unique IDs but 5 input items
        // After dedup we have 4 unique chunks; only first 3 should be included
        var limitedAdapter = new DefaultContextAssemblyAdapter(3);
        ChunkId dupId = ChunkId.generate();

        List<RetrievedChunk> chunks = List.of(
                chunk(dupId, "first"),
                chunk("second"),
                chunk(dupId, "duplicate"),   // deduped — not counted
                chunk("third"),
                chunk("fourth"));

        AssembledContext ctx = limitedAdapter.assemble(chunks, QUERY, LARGE_TOKEN_BUDGET);

        assertThat(ctx.size()).isEqualTo(3);
        assertThat(ctx.chunks().get(0).content()).isEqualTo("first");
        assertThat(ctx.chunks().get(1).content()).isEqualTo("second");
        assertThat(ctx.chunks().get(2).content()).isEqualTo("third");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RetrievedChunk chunk(String content) {
        return chunk(ChunkId.generate(), content);
    }

    private RetrievedChunk chunk(ChunkId chunkId, String content) {
        return new RetrievedChunk(chunkId, docId, tenantId, content, 0.5, 0);
    }
}
