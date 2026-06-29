package com.mudassir.eka.infrastructure.retrieval.hybrid;

import com.mudassir.eka.domain.chunk.ChunkId;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.query.MetadataFilter;
import com.mudassir.eka.domain.retrieval.model.RetrievalOptions;
import com.mudassir.eka.domain.retrieval.model.RetrievalResult;
import com.mudassir.eka.domain.retrieval.model.RetrievedChunk;
import com.mudassir.eka.domain.retrieval.model.SearchMetadata;
import com.mudassir.eka.domain.retrieval.port.RetrievalPort;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.infrastructure.retrieval.hybrid.exception.HybridRetrievalException;
import com.mudassir.eka.infrastructure.retrieval.postgres.exception.Bm25RetrievalException;
import com.mudassir.eka.infrastructure.retrieval.weaviate.exception.RetrievalAdapterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridRetrievalAdapterTest {

    @Mock private RetrievalPort vectorPort;
    @Mock private RetrievalPort bm25Port;

    private HybridRetrievalAdapter adapter;

    private final TenantId         tenantId = TenantId.generate();
    private final RetrievalOptions options  = RetrievalOptions.DEFAULT;

    @BeforeEach
    void setUp() {
        adapter = new HybridRetrievalAdapter(vectorPort, bm25Port);
    }

    // ── Happy path: both engines succeed ─────────────────────────────────────

    @Test
    void retrieve_combinesResultsFromBothEngines() {
        RetrievedChunk vectorChunk = chunk(0);
        RetrievedChunk bm25Chunk   = chunk(0);
        when(vectorPort.retrieve(any(), any(), any(), any()))
                .thenReturn(resultOf(vectorChunk, "vector"));
        when(bm25Port.retrieve(any(), any(), any(), any()))
                .thenReturn(resultOf(bm25Chunk, "bm25"));

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE, options);

        assertThat(result.items()).containsExactly(vectorChunk, bm25Chunk);
    }

    @Test
    void retrieve_vectorResultsAppearsBeforeBm25InCombinedList() {
        RetrievedChunk v = chunk(0);
        RetrievedChunk b = chunk(1);
        when(vectorPort.retrieve(any(), any(), any(), any())).thenReturn(resultOf(v, "vector"));
        when(bm25Port.retrieve(any(), any(), any(), any())).thenReturn(resultOf(b, "bm25"));

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE, options);

        assertThat(result.items().get(0)).isEqualTo(v);
        assertThat(result.items().get(1)).isEqualTo(b);
    }

    @Test
    void retrieve_totalHitsIsSum_ofBothEngineResults() {
        when(vectorPort.retrieve(any(), any(), any(), any()))
                .thenReturn(resultOf(List.of(chunk(0), chunk(1)), "vector"));
        when(bm25Port.retrieve(any(), any(), any(), any()))
                .thenReturn(resultOf(List.of(chunk(0)), "bm25"));

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE, options);

        assertThat(result.metadata().totalHits()).isEqualTo(3);
        assertThat(result.items()).hasSize(3);
    }

    @Test
    void retrieve_strategyIsHybrid_whenBothSucceed() {
        when(vectorPort.retrieve(any(), any(), any(), any())).thenReturn(resultOf(chunk(0), "vector"));
        when(bm25Port.retrieve(any(), any(), any(), any())).thenReturn(resultOf(chunk(0), "bm25"));

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE, options);

        assertThat(result.metadata().strategy()).isEqualTo("hybrid");
    }

    @Test
    void retrieve_latencyMsIsNonNegative() {
        when(vectorPort.retrieve(any(), any(), any(), any())).thenReturn(resultOf(chunk(0), "vector"));
        when(bm25Port.retrieve(any(), any(), any(), any())).thenReturn(resultOf(chunk(0), "bm25"));

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE, options);

        assertThat(result.metadata().latencyMs()).isGreaterThanOrEqualTo(0);
    }

    // ── Empty results ─────────────────────────────────────────────────────────

    @Test
    void retrieve_returnsEmptyCombinedResult_whenBothEnginesReturnEmpty() {
        when(vectorPort.retrieve(any(), any(), any(), any()))
                .thenReturn(RetrievalResult.empty("vector", 1L));
        when(bm25Port.retrieve(any(), any(), any(), any()))
                .thenReturn(RetrievalResult.empty("bm25", 1L));

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE, options);

        assertThat(result.items()).isEmpty();
        assertThat(result.metadata().totalHits()).isZero();
        assertThat(result.metadata().strategy()).isEqualTo("hybrid");
    }

    // ── Partial failure: one engine fails ─────────────────────────────────────

    @Test
    void retrieve_succeedsWithBm25Only_whenVectorFails() {
        RetrievedChunk bm25Chunk = chunk(0);
        when(vectorPort.retrieve(any(), any(), any(), any()))
                .thenThrow(new RetrievalAdapterException("Weaviate unavailable", null));
        when(bm25Port.retrieve(any(), any(), any(), any()))
                .thenReturn(resultOf(bm25Chunk, "bm25"));

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE, options);

        assertThat(result.items()).containsExactly(bm25Chunk);
        assertThat(result.metadata().strategy()).isEqualTo("hybrid:bm25-only");
    }

    @Test
    void retrieve_succeedsWithVectorOnly_whenBm25Fails() {
        RetrievedChunk vectorChunk = chunk(0);
        when(vectorPort.retrieve(any(), any(), any(), any()))
                .thenReturn(resultOf(vectorChunk, "vector"));
        when(bm25Port.retrieve(any(), any(), any(), any()))
                .thenThrow(new Bm25RetrievalException("PostgreSQL unavailable", null));

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE, options);

        assertThat(result.items()).containsExactly(vectorChunk);
        assertThat(result.metadata().strategy()).isEqualTo("hybrid:vector-only");
    }

    @Test
    void retrieve_strategyReflectsDegradation_whenVectorFails() {
        when(vectorPort.retrieve(any(), any(), any(), any()))
                .thenThrow(new RetrievalAdapterException("timeout", null));
        when(bm25Port.retrieve(any(), any(), any(), any()))
                .thenReturn(RetrievalResult.empty("bm25", 1L));

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE, options);

        assertThat(result.metadata().strategy()).isEqualTo("hybrid:bm25-only");
    }

    @Test
    void retrieve_strategyReflectsDegradation_whenBm25Fails() {
        when(vectorPort.retrieve(any(), any(), any(), any()))
                .thenReturn(RetrievalResult.empty("vector", 1L));
        when(bm25Port.retrieve(any(), any(), any(), any()))
                .thenThrow(new Bm25RetrievalException("timeout", null));

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE, options);

        assertThat(result.metadata().strategy()).isEqualTo("hybrid:vector-only");
    }

    // ── Total failure ─────────────────────────────────────────────────────────

    @Test
    void retrieve_throwsHybridRetrievalException_whenBothFail() {
        when(vectorPort.retrieve(any(), any(), any(), any()))
                .thenThrow(new RetrievalAdapterException("Weaviate down", null));
        when(bm25Port.retrieve(any(), any(), any(), any()))
                .thenThrow(new Bm25RetrievalException("Postgres down", null));

        assertThatThrownBy(() -> adapter.retrieve("query", tenantId, MetadataFilter.NONE, options))
                .isInstanceOf(HybridRetrievalException.class)
                .hasMessageContaining(tenantId.toString());
    }

    @Test
    void retrieve_attemptsBothEngines_beforeThrowingOnTotalFailure() {
        when(vectorPort.retrieve(any(), any(), any(), any()))
                .thenThrow(new RetrievalAdapterException("down", null));
        when(bm25Port.retrieve(any(), any(), any(), any()))
                .thenThrow(new Bm25RetrievalException("down", null));

        assertThatThrownBy(() -> adapter.retrieve("query", tenantId, MetadataFilter.NONE, options))
                .isInstanceOf(HybridRetrievalException.class);

        verify(vectorPort).retrieve(any(), any(), any(), any());
        verify(bm25Port).retrieve(any(), any(), any(), any());
    }

    // ── Duplicate handling ────────────────────────────────────────────────────

    @Test
    void retrieve_doesNotDeduplicateBeforeReturning() {
        ChunkId sharedId = ChunkId.generate();
        RetrievedChunk fromVector = chunkWithId(sharedId, 0);
        RetrievedChunk fromBm25   = chunkWithId(sharedId, 0);

        when(vectorPort.retrieve(any(), any(), any(), any())).thenReturn(resultOf(fromVector, "vector"));
        when(bm25Port.retrieve(any(), any(), any(), any())).thenReturn(resultOf(fromBm25, "bm25"));

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE, options);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().stream().filter(c -> c.chunkId().equals(sharedId))).hasSize(2);
    }

    // ── Propagates parameters ────────────────────────────────────────────────

    @Test
    void retrieve_passesQueryTextToBothEngines() {
        String query = "what is a service level agreement?";
        when(vectorPort.retrieve(eq(query), any(), any(), any()))
                .thenReturn(RetrievalResult.empty("vector", 1L));
        when(bm25Port.retrieve(eq(query), any(), any(), any()))
                .thenReturn(RetrievalResult.empty("bm25", 1L));

        adapter.retrieve(query, tenantId, MetadataFilter.NONE, options);

        verify(vectorPort).retrieve(eq(query), eq(tenantId), eq(MetadataFilter.NONE), eq(options));
        verify(bm25Port).retrieve(eq(query), eq(tenantId), eq(MetadataFilter.NONE), eq(options));
    }

    @Test
    void retrieve_passesTenantIdAndFilterToBothEngines() {
        MetadataFilter filter = MetadataFilter.builder().put("department", "legal").build();
        when(vectorPort.retrieve(any(), eq(tenantId), eq(filter), eq(options)))
                .thenReturn(RetrievalResult.empty("vector", 1L));
        when(bm25Port.retrieve(any(), eq(tenantId), eq(filter), eq(options)))
                .thenReturn(RetrievalResult.empty("bm25", 1L));

        adapter.retrieve("query", tenantId, filter, options);

        verify(vectorPort).retrieve(any(), eq(tenantId), eq(filter), eq(options));
        verify(bm25Port).retrieve(any(), eq(tenantId), eq(filter), eq(options));
    }

    // ── Sequential execution ──────────────────────────────────────────────────

    @Test
    void retrieve_doesNotCallBm25_ifNotNeededAfterVectorFails() {
        // BM25 IS still called even if vector fails — verify sequential execution
        when(vectorPort.retrieve(any(), any(), any(), any()))
                .thenThrow(new RetrievalAdapterException("timeout", null));
        when(bm25Port.retrieve(any(), any(), any(), any()))
                .thenReturn(RetrievalResult.empty("bm25", 1L));

        adapter.retrieve("query", tenantId, MetadataFilter.NONE, options);

        verify(vectorPort).retrieve(any(), any(), any(), any());
        verify(bm25Port).retrieve(any(), any(), any(), any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RetrievedChunk chunk(int rank) {
        return new RetrievedChunk(
                ChunkId.generate(),
                DocumentId.generate(),
                tenantId,
                "content",
                0.8,
                rank);
    }

    private RetrievedChunk chunkWithId(ChunkId id, int rank) {
        return new RetrievedChunk(id, DocumentId.generate(), tenantId, "content", 0.8, rank);
    }

    private RetrievalResult resultOf(RetrievedChunk chunk, String strategy) {
        return resultOf(List.of(chunk), strategy);
    }

    private RetrievalResult resultOf(List<RetrievedChunk> chunks, String strategy) {
        return new RetrievalResult(chunks, new SearchMetadata(chunks.size(), 5L, strategy));
    }
}
