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

    private void stubVectorStoreIndexToAssignIds(List<Chunk> chunks) {
        doAnswer(inv -> {
            List<Chunk> args = inv.getArgument(0);
            args.forEach(c -> c.assignVectorId(UUID.randomUUID().toString()));
            return null;
        }).when(vectorStore).index(anyList());
    }

    @Test
    void index_returnsEmptyList_forEmptyInput() {
        assertThat(service.index(List.of())).isEmpty();
        verify(vectorStore, never()).index(anyList());
    }

    @Test
    void index_callsVectorStoreAndPersistsChunks() {
        Chunk chunk = freshChunk();
        stubVectorStoreIndexToAssignIds(List.of(chunk));
        when(chunkRepository.saveAll(anyList())).thenReturn(List.of(chunk));

        List<Chunk> result = service.index(List.of(chunk));

        assertThat(result).hasSize(1);
        verify(vectorStore).index(anyList());
        verify(chunkRepository).saveAll(anyList());
    }

    @Test
    void index_publishesChunkIndexedEventPerChunk() {
        Chunk chunk = freshChunk();
        stubVectorStoreIndexToAssignIds(List.of(chunk));
        when(chunkRepository.saveAll(anyList())).thenReturn(List.of(chunk));

        service.index(List.of(chunk));

        ArgumentCaptor<ChunkIndexedEvent> captor = ArgumentCaptor.forClass(ChunkIndexedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("chunk.indexed");
        assertThat(captor.getValue().getVectorId()).isNotNull();
    }

    @Test
    void index_deletesStaleVectorsBeforeReindexing() {
        // Chunk with a stale vectorId from a previous indexing run
        Chunk staleChunk = Chunk.reconstitute(
                freshChunk().getId(), docId, tenantId, 0, "content",
                ChunkMetadata.of("sliding-window"),
                "old-vector-id", "nomic-embed-text", 768, Instant.now(), Instant.now());
        stubVectorStoreIndexToAssignIds(List.of(staleChunk));
        when(chunkRepository.saveAll(anyList())).thenReturn(List.of(staleChunk));

        service.index(List.of(staleChunk));

        verify(vectorStore).deleteByIds(List.of("old-vector-id"));
        verify(vectorStore).index(anyList());
    }

    @Test
    void index_clearsVectorIdBeforeReindexing_soAssignVectorIdDoesNotThrow() {
        Chunk staleChunk = Chunk.reconstitute(
                freshChunk().getId(), docId, tenantId, 0, "content",
                ChunkMetadata.of("sliding-window"),
                "stale-id", "nomic-embed-text", 768, Instant.now(), Instant.now());

        doAnswer(inv -> {
            List<Chunk> args = inv.getArgument(0);
            // clearVectorId should have been called before index(); assignVectorId must not throw
            args.forEach(c -> c.assignVectorId(UUID.randomUUID().toString()));
            return null;
        }).when(vectorStore).index(anyList());
        when(chunkRepository.saveAll(anyList())).thenReturn(List.of(staleChunk));

        service.index(List.of(staleChunk));

        // If we reach here without IllegalStateException, clearVectorId worked correctly
        verify(vectorStore).index(anyList());
    }

    @Test
    void index_doesNotDeleteVectorsWhenNoneExist() {
        Chunk chunk = freshChunk(); // vectorId is null
        stubVectorStoreIndexToAssignIds(List.of(chunk));
        when(chunkRepository.saveAll(anyList())).thenReturn(List.of(chunk));

        service.index(List.of(chunk));

        verify(vectorStore, never()).deleteByIds(any());
    }
}
