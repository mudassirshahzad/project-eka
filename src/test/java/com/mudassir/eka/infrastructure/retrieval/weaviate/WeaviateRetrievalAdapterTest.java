package com.mudassir.eka.infrastructure.retrieval.weaviate;

import com.mudassir.eka.domain.chunk.Chunk;
import com.mudassir.eka.domain.chunk.ChunkId;
import com.mudassir.eka.domain.chunk.ChunkMetadata;
import com.mudassir.eka.domain.chunk.ChunkRepository;
import com.mudassir.eka.domain.chunk.VectorSearchResult;
import com.mudassir.eka.domain.chunk.VectorStore;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.query.MetadataFilter;
import com.mudassir.eka.domain.retrieval.model.RetrievalOptions;
import com.mudassir.eka.domain.retrieval.model.RetrievalResult;
import com.mudassir.eka.domain.retrieval.model.RetrievedChunk;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.infrastructure.retrieval.weaviate.exception.RetrievalAdapterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeaviateRetrievalAdapterTest {

    @Mock private VectorStore     vectorStore;
    @Mock private ChunkRepository chunkRepository;

    private RetrievedChunkMapper     mapper;
    private WeaviateRetrievalAdapter adapter;

    private final TenantId   tenantId   = TenantId.generate();
    private final DocumentId documentId = DocumentId.generate();

    @BeforeEach
    void setUp() {
        mapper  = new RetrievedChunkMapper();
        adapter = new WeaviateRetrievalAdapter(vectorStore, chunkRepository, mapper);
    }

    // ── Empty result paths ────────────────────────────────────────────────────

    @Test
    void retrieve_returnsEmptyResultWhenVectorStoreReturnsNoHits() {
        when(vectorStore.search(any(), anyInt(), any())).thenReturn(List.of());

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        assertThat(result.hasResults()).isFalse();
        assertThat(result.metadata().strategy()).isEqualTo("vector");
        verifyNoInteractions(chunkRepository);
    }

    @Test
    void retrieve_returnsEmptyResultWhenAllChunkIdsAreNull() {
        var resultWithNullId = new VectorSearchResult(null, "v1", "content", 0.9);
        when(vectorStore.search(any(), anyInt(), any())).thenReturn(List.of(resultWithNullId));

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        assertThat(result.hasResults()).isFalse();
        verifyNoInteractions(chunkRepository);
    }

    @Test
    void retrieve_skipsResultsWithNoMatchingChunkInDatabase() {
        ChunkId chunkId = ChunkId.generate();
        when(vectorStore.search(any(), anyInt(), any()))
                .thenReturn(List.of(new VectorSearchResult(chunkId, "v1", "content", 0.9)));
        when(chunkRepository.findByIds(any())).thenReturn(List.of());

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        assertThat(result.hasResults()).isFalse();
    }

    // ── Successful retrieval ──────────────────────────────────────────────────

    @Test
    void retrieve_mapsResultsCorrectly() {
        ChunkId chunkId = ChunkId.generate();
        Chunk   chunk   = sampleChunk(chunkId);
        when(vectorStore.search(any(), anyInt(), any()))
                .thenReturn(List.of(new VectorSearchResult(chunkId, "v1", "indexed content", 0.88)));
        when(chunkRepository.findByIds(List.of(chunkId))).thenReturn(List.of(chunk));

        RetrievalResult result = adapter.retrieve("query text", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        assertThat(result.hasResults()).isTrue();
        assertThat(result.size()).isEqualTo(1);
        RetrievedChunk item = result.items().get(0);
        assertThat(item.chunkId()).isEqualTo(chunkId);
        assertThat(item.documentId()).isEqualTo(documentId);
        assertThat(item.tenantId()).isEqualTo(tenantId);
        assertThat(item.content()).isEqualTo("indexed content");
        assertThat(item.score()).isEqualTo(0.88);
        assertThat(item.rank()).isEqualTo(0);
        assertThat(result.metadata().strategy()).isEqualTo("vector");
    }

    @Test
    void retrieve_assignsSequentialRanksStartingFromZero() {
        ChunkId id1 = ChunkId.generate();
        ChunkId id2 = ChunkId.generate();
        ChunkId id3 = ChunkId.generate();
        when(vectorStore.search(any(), anyInt(), any())).thenReturn(List.of(
                new VectorSearchResult(id1, "v1", "c1", 0.9),
                new VectorSearchResult(id2, "v2", "c2", 0.8),
                new VectorSearchResult(id3, "v3", "c3", 0.7)));
        when(chunkRepository.findByIds(any())).thenReturn(List.of(
                sampleChunk(id1), sampleChunk(id2), sampleChunk(id3)));

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        assertThat(result.items()).extracting(RetrievedChunk::rank).containsExactly(0, 1, 2);
    }

    // ── Tenant isolation ──────────────────────────────────────────────────────

    @Test
    void retrieve_alwaysIncludesTenantIdInSearchFilter() {
        when(vectorStore.search(any(), anyInt(), any())).thenReturn(List.of());

        adapter.retrieve("query", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        ArgumentCaptor<MetadataFilter> captor = ArgumentCaptor.forClass(MetadataFilter.class);
        verify(vectorStore).search(any(), anyInt(), captor.capture());
        assertThat(captor.getValue().criteria())
                .containsEntry("tenantId", tenantId.value().toString());
    }

    @Test
    void retrieve_tenantIdOverridesAnyCallerSuppliedTenantId() {
        when(vectorStore.search(any(), anyInt(), any())).thenReturn(List.of());
        MetadataFilter maliciousFilter = MetadataFilter.builder()
                .put("tenantId", "attacker-tenant-uuid").build();

        adapter.retrieve("query", tenantId, maliciousFilter, RetrievalOptions.DEFAULT);

        ArgumentCaptor<MetadataFilter> captor = ArgumentCaptor.forClass(MetadataFilter.class);
        verify(vectorStore).search(any(), anyInt(), captor.capture());
        assertThat(captor.getValue().criteria().get("tenantId"))
                .isEqualTo(tenantId.value().toString());
    }

    // ── Metadata filtering ────────────────────────────────────────────────────

    @Test
    void retrieve_combinesUserFilterCriteriaWithTenantId() {
        when(vectorStore.search(any(), anyInt(), any())).thenReturn(List.of());
        MetadataFilter userFilter = MetadataFilter.builder()
                .department("engineering")
                .classification("internal")
                .build();

        adapter.retrieve("query", tenantId, userFilter, RetrievalOptions.DEFAULT);

        ArgumentCaptor<MetadataFilter> captor = ArgumentCaptor.forClass(MetadataFilter.class);
        verify(vectorStore).search(any(), anyInt(), captor.capture());
        assertThat(captor.getValue().criteria())
                .containsEntry("department", "engineering")
                .containsEntry("classification", "internal")
                .containsEntry("tenantId", tenantId.value().toString());
    }

    // ── RetrievalOptions ──────────────────────────────────────────────────────

    @Test
    void retrieve_passesTopKFromOptionsToVectorStore() {
        when(vectorStore.search(any(), anyInt(), any())).thenReturn(List.of());
        RetrievalOptions options = RetrievalOptions.of(5);

        adapter.retrieve("query", tenantId, MetadataFilter.NONE, options);

        verify(vectorStore).search(any(), eq(5), any());
    }

    @Test
    void retrieve_filtersBelowMinimumScore() {
        ChunkId id1 = ChunkId.generate();
        ChunkId id2 = ChunkId.generate();
        when(vectorStore.search(any(), anyInt(), any())).thenReturn(List.of(
                new VectorSearchResult(id1, "v1", "above threshold", 0.8),
                new VectorSearchResult(id2, "v2", "below threshold", 0.3)));
        when(chunkRepository.findByIds(any())).thenReturn(List.of(
                sampleChunk(id1), sampleChunk(id2)));

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE,
                RetrievalOptions.of(10, 0.5));

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.items().get(0).score()).isGreaterThanOrEqualTo(0.5);
    }

    @Test
    void retrieve_preservesOriginalRawRankWhenItemsAreFilteredOut() {
        ChunkId id0 = ChunkId.generate();
        ChunkId id1 = ChunkId.generate(); // will be filtered (score below threshold)
        ChunkId id2 = ChunkId.generate();
        when(vectorStore.search(any(), anyInt(), any())).thenReturn(List.of(
                new VectorSearchResult(id0, "v0", "c0", 0.9),
                new VectorSearchResult(id1, "v1", "c1", 0.3),  // filtered
                new VectorSearchResult(id2, "v2", "c2", 0.8)));
        when(chunkRepository.findByIds(any())).thenReturn(List.of(
                sampleChunk(id0), sampleChunk(id1), sampleChunk(id2)));

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE,
                RetrievalOptions.of(10, 0.5));

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.items()).extracting(RetrievedChunk::rank)
                .containsExactly(0, 2);
    }

    @Test
    void retrieve_includesResultsAtExactMinimumScoreBoundary() {
        ChunkId chunkId = ChunkId.generate();
        when(vectorStore.search(any(), anyInt(), any()))
                .thenReturn(List.of(new VectorSearchResult(chunkId, "v1", "content", 0.5)));
        when(chunkRepository.findByIds(any())).thenReturn(List.of(sampleChunk(chunkId)));

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE,
                RetrievalOptions.of(10, 0.5));

        assertThat(result.hasResults()).isTrue();
    }

    // ── Score normalization ───────────────────────────────────────────────────

    @Test
    void retrieve_normalizesScoresToUnitRange() {
        ChunkId chunkId = ChunkId.generate();
        when(vectorStore.search(any(), anyInt(), any()))
                .thenReturn(List.of(new VectorSearchResult(chunkId, "v1", "content", 1.5)));
        when(chunkRepository.findByIds(any())).thenReturn(List.of(sampleChunk(chunkId)));

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        assertThat(result.items().get(0).score()).isLessThanOrEqualTo(1.0);
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    void retrieve_translatesVectorStoreExceptionToRetrievalAdapterException() {
        when(vectorStore.search(any(), anyInt(), any()))
                .thenThrow(new RuntimeException("Weaviate connection refused"));

        assertThatExceptionOfType(RetrievalAdapterException.class)
                .isThrownBy(() -> adapter.retrieve("query", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT));
    }

    @Test
    void retrieve_translatesChunkRepositoryExceptionToRetrievalAdapterException() {
        ChunkId chunkId = ChunkId.generate();
        when(vectorStore.search(any(), anyInt(), any()))
                .thenReturn(List.of(new VectorSearchResult(chunkId, "v1", "content", 0.9)));
        when(chunkRepository.findByIds(any()))
                .thenThrow(new RuntimeException("Database unavailable"));

        assertThatExceptionOfType(RetrievalAdapterException.class)
                .isThrownBy(() -> adapter.retrieve("query", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT));
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private Chunk sampleChunk(ChunkId chunkId) {
        return Chunk.reconstitute(
                chunkId, documentId, tenantId, 0, "chunk content",
                ChunkMetadata.of("sliding-window"),
                "vector-id-001", "nomic-embed-text", 768,
                Instant.now(), Instant.now());
    }
}
