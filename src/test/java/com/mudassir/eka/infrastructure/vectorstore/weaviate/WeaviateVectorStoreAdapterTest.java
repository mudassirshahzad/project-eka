package com.mudassir.eka.infrastructure.vectorstore.weaviate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mudassir.eka.domain.chunk.Chunk;
import com.mudassir.eka.domain.chunk.ChunkMetadata;
import com.mudassir.eka.domain.chunk.VectorSearchResult;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.query.MetadataFilter;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.infrastructure.config.AppProperties;
import com.mudassir.eka.infrastructure.vectorstore.weaviate.exception.VectorIndexingException;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.v1.batch.Batch;
import io.weaviate.client.v1.batch.api.ObjectsBatcher;
import io.weaviate.client.v1.batch.model.ObjectGetResponse;
import io.weaviate.client.v1.data.model.WeaviateObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeaviateVectorStoreAdapterTest {

    @Mock private org.springframework.ai.vectorstore.VectorStore springVectorStore;
    @Mock private WeaviateClient                                 weaviateClient;
    @Mock private MetadataFilterTranslator                       filterTranslator;
    @Mock private AppProperties                                  appProperties;

    private WeaviateVectorStoreAdapter adapter;

    private final DocumentId docId    = DocumentId.generate();
    private final TenantId   tenantId = TenantId.generate();

    @BeforeEach
    void setUp() {
        AppProperties.Ingestion ingestion = mock(AppProperties.Ingestion.class);
        lenient().when(ingestion.embeddingBatchSize()).thenReturn(32);
        lenient().when(appProperties.ingestion()).thenReturn(ingestion);

        adapter = new WeaviateVectorStoreAdapter(
                springVectorStore,
                weaviateClient,
                filterTranslator,
                appProperties,
                new ObjectMapper(),
                "DocumentChunk",
                "ONE");
    }

    private Chunk freshChunk() {
        Chunk c = Chunk.create(docId, tenantId, 0, "chunk text", ChunkMetadata.of("sliding-window"));
        c.assignEmbeddingProvenance("nomic-embed-text", 3, Instant.now());
        return c;
    }

    @SuppressWarnings("unchecked")
    private ObjectsBatcher stubBatcher() {
        Result<ObjectGetResponse[]> result = (Result<ObjectGetResponse[]>) mock(Result.class);
        when(result.hasErrors()).thenReturn(false);

        Batch batch = mock(Batch.class);
        ObjectsBatcher batcher = mock(ObjectsBatcher.class);
        when(weaviateClient.batch()).thenReturn(batch);
        when(batch.objectsBatcher()).thenReturn(batcher);
        when(batcher.withObjects(any())).thenReturn(batcher);
        when(batcher.withConsistencyLevel(anyString())).thenReturn(batcher);
        when(batcher.run()).thenReturn(result);
        return batcher;
    }

    // ── index: basic contract ─────────────────────────────────────────────────

    @Test
    void index_emptyInput_doesNothing() {
        adapter.index(List.of(), List.of());
        verify(weaviateClient, never()).batch();
    }

    @Test
    void index_callsWeaviateClientBatchWithPrecomputedVector() {
        Chunk chunk = freshChunk();
        float[] vector = {0.1f, 0.2f, 0.3f};
        ObjectsBatcher batcher = stubBatcher();

        adapter.index(List.of(chunk), List.of(vector));

        ArgumentCaptor<WeaviateObject[]> captor = ArgumentCaptor.forClass(WeaviateObject[].class);
        verify(batcher).withObjects(captor.capture());

        WeaviateObject obj = captor.getValue()[0];
        assertThat(obj.getVector()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(obj.getClassName()).isEqualTo("DocumentChunk");
    }

    @Test
    void index_doesNotCallSpringVectorStoreAdd() {
        stubBatcher();
        adapter.index(List.of(freshChunk()), List.of(new float[]{0.1f}));
        verify(springVectorStore, never()).add(any());
    }

    @Test
    void index_assignsVectorIdToChunkAfterSuccess() {
        Chunk chunk = freshChunk();
        assertThat(chunk.getVectorId()).isNull();
        stubBatcher();

        adapter.index(List.of(chunk), List.of(new float[]{0.1f}));

        assertThat(chunk.getVectorId()).isNotNull();
    }

    @Test
    void index_storesContentAndMetadataInWeaviateProperties() {
        Chunk chunk = freshChunk();
        ObjectsBatcher batcher = stubBatcher();

        adapter.index(List.of(chunk), List.of(new float[]{0.1f}));

        ArgumentCaptor<WeaviateObject[]> captor = ArgumentCaptor.forClass(WeaviateObject[].class);
        verify(batcher).withObjects(captor.capture());
        WeaviateObject obj = captor.getValue()[0];

        assertThat(obj.getProperties()).containsKey("content");
        assertThat(obj.getProperties().get("content")).isEqualTo("chunk text");
        assertThat(obj.getProperties()).containsKey("metadata");
        assertThat(obj.getProperties()).containsKey("meta_chunkId");
        assertThat(obj.getProperties()).containsKey("meta_tenantId");
    }

    @Test
    void index_rejectsMismatchedChunkAndVectorCounts() {
        // setUp() stub for batchSize isn't invoked — this test throws before reaching the batcher
        Chunk chunk = freshChunk();
        assertThatThrownBy(() -> adapter.index(List.of(chunk), List.of()))
                .isInstanceOf(VectorIndexingException.class)
                .hasMessageContaining("1")
                .hasMessageContaining("0");
    }

    @Test
    void index_doesNotAssignVectorIdIfBatchFails() {
        Chunk chunk = freshChunk();

        Batch batch = mock(Batch.class);
        ObjectsBatcher batcher = mock(ObjectsBatcher.class);
        when(weaviateClient.batch()).thenReturn(batch);
        when(batch.objectsBatcher()).thenReturn(batcher);
        when(batcher.withObjects(any())).thenReturn(batcher);
        when(batcher.withConsistencyLevel(anyString())).thenReturn(batcher);
        when(batcher.run()).thenThrow(new RuntimeException("Weaviate down"));

        assertThatThrownBy(() -> adapter.index(List.of(chunk), List.of(new float[]{0.1f})))
                .isInstanceOf(VectorIndexingException.class);

        assertThat(chunk.getVectorId()).isNull();
    }

    // ── search: delegates to Spring AI VectorStore ────────────────────────────

    @Test
    void search_delegatesToSpringVectorStore() {
        when(filterTranslator.translate(any())).thenReturn(null);
        when(springVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

        List<VectorSearchResult> results = adapter.search("query", 5, MetadataFilter.NONE);

        assertThat(results).isEmpty();
        verify(springVectorStore).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void search_mapsSpringAiDocumentToVectorSearchResult() {
        String chunkUuid = UUID.randomUUID().toString();
        when(filterTranslator.translate(any())).thenReturn(mock(Filter.Expression.class));
        Document doc = mock(Document.class);
        when(doc.getMetadata()).thenReturn(Map.of("chunkId", chunkUuid));
        when(doc.getId()).thenReturn("vector-id-1");
        when(doc.getText()).thenReturn("chunk text");
        when(doc.getScore()).thenReturn(0.9);
        when(springVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        List<VectorSearchResult> results = adapter.search("query", 5, MetadataFilter.NONE);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).score()).isEqualTo(0.9);
        assertThat(results.get(0).content()).isEqualTo("chunk text");
    }
}
