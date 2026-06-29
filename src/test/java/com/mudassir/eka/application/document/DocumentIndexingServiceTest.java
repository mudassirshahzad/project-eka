package com.mudassir.eka.application.document;

import com.mudassir.eka.application.event.ChunkIndexedEvent;
import com.mudassir.eka.application.shared.DomainEventPublisher;
import com.mudassir.eka.domain.chunk.Chunk;
import com.mudassir.eka.domain.chunk.ChunkMetadata;
import com.mudassir.eka.domain.chunk.ChunkRepository;
import com.mudassir.eka.domain.chunk.VectorStore;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.shared.TenantId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentIndexingServiceTest {

    @Mock private VectorStore          vectorStore;
    @Mock private ChunkRepository      chunkRepository;
    @Mock private DomainEventPublisher eventPublisher;
    @InjectMocks private DocumentIndexingService service;

    private final DocumentId docId    = DocumentId.generate();
    private final TenantId   tenantId = TenantId.generate();

    private Chunk freshChunk() {
        Chunk c = Chunk.create(docId, tenantId, 0, "content", ChunkMetadata.of("sliding-window"));
        c.assignEmbeddingProvenance("nomic-embed-text", 768, Instant.now());
        return c;
    }

    private EmbeddedChunk embedded(Chunk chunk) {
        return new EmbeddedChunk(chunk, new float[768]);
    }

    /** Stubs vectorStore.index(chunks, vectors) to assign a random vectorId to each chunk. */
    @SuppressWarnings("unchecked")
    private void stubVectorStoreIndexToAssignIds() {
        doAnswer(inv -> {
            List<Chunk> chunks = inv.getArgument(0);
            chunks.forEach(c -> c.assignVectorId(UUID.randomUUID().toString()));
            return null;
        }).when(vectorStore).index(anyList(), anyList());
    }

    // ── empty input ───────────────────────────────────────────────────────────

    @Test
    void index_returnsEmptyList_forEmptyInput() {
        assertThat(service.index(List.of())).isEmpty();
        verify(vectorStore, never()).index(anyList(), anyList());
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void index_callsVectorStoreAndPersistsChunks() {
        Chunk chunk = freshChunk();
        stubVectorStoreIndexToAssignIds();
        when(chunkRepository.saveAll(anyList())).thenReturn(List.of(chunk));

        List<Chunk> result = service.index(List.of(embedded(chunk)));

        assertThat(result).hasSize(1);
        verify(vectorStore).index(anyList(), anyList());
        verify(chunkRepository).saveAll(anyList());
    }

    @Test
    void index_passesPrecomputedVectorsToVectorStore() {
        Chunk   chunk  = freshChunk();
        float[] vector = new float[]{0.1f, 0.2f, 0.3f};
        stubVectorStoreIndexToAssignIds();
        when(chunkRepository.saveAll(anyList())).thenReturn(List.of(chunk));

        service.index(List.of(new EmbeddedChunk(chunk, vector)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<float[]>> vectorCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).index(anyList(), vectorCaptor.capture());
        assertThat(vectorCaptor.getValue()).hasSize(1);
        assertThat(vectorCaptor.getValue().get(0)).isEqualTo(vector);
    }

    @Test
    void index_publishesChunkIndexedEventPerChunk() {
        Chunk chunk = freshChunk();
        stubVectorStoreIndexToAssignIds();
        when(chunkRepository.saveAll(anyList())).thenReturn(List.of(chunk));

        service.index(List.of(embedded(chunk)));

        ArgumentCaptor<ChunkIndexedEvent> captor = ArgumentCaptor.forClass(ChunkIndexedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("chunk.indexed");
        assertThat(captor.getValue().getVectorId()).isNotNull();
    }

    // ── idempotency / stale vector cleanup ───────────────────────────────────

    @Test
    void index_deletesStaleVectorsBeforeReindexing() {
        Chunk staleChunk = Chunk.reconstitute(
                freshChunk().getId(), docId, tenantId, 0, "content",
                ChunkMetadata.of("sliding-window"),
                "old-vector-id", "nomic-embed-text", 768, Instant.now(), Instant.now());
        stubVectorStoreIndexToAssignIds();
        when(chunkRepository.saveAll(anyList())).thenReturn(List.of(staleChunk));

        service.index(List.of(embedded(staleChunk)));

        verify(vectorStore).deleteByIds(List.of("old-vector-id"));
        verify(vectorStore).index(anyList(), anyList());
    }

    @Test
    void index_clearsVectorIdBeforeReindexing_soAssignVectorIdDoesNotThrow() {
        Chunk staleChunk = Chunk.reconstitute(
                freshChunk().getId(), docId, tenantId, 0, "content",
                ChunkMetadata.of("sliding-window"),
                "stale-id", "nomic-embed-text", 768, Instant.now(), Instant.now());

        doAnswer(inv -> {
            List<Chunk> chunks = inv.getArgument(0);
            // clearVectorId must have been called before index(); assignVectorId must not throw
            chunks.forEach(c -> c.assignVectorId(UUID.randomUUID().toString()));
            return null;
        }).when(vectorStore).index(anyList(), anyList());
        when(chunkRepository.saveAll(anyList())).thenReturn(List.of(staleChunk));

        service.index(List.of(embedded(staleChunk)));

        // No IllegalStateException thrown means clearVectorId worked correctly
        verify(vectorStore).index(anyList(), anyList());
    }

    @Test
    void index_doesNotDeleteVectorsWhenNoneExist() {
        Chunk chunk = freshChunk(); // vectorId is null
        stubVectorStoreIndexToAssignIds();
        when(chunkRepository.saveAll(anyList())).thenReturn(List.of(chunk));

        service.index(List.of(embedded(chunk)));

        verify(vectorStore, never()).deleteByIds(any());
    }
}
