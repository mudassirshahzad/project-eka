package com.mudassir.eka.application.document;

import com.mudassir.eka.domain.chunk.Chunk;
import com.mudassir.eka.domain.chunk.ChunkMetadata;
import com.mudassir.eka.domain.chunk.ChunkRepository;
import com.mudassir.eka.domain.chunk.VectorStore;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.document.DocumentMetadata;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteDocumentUseCaseTest {

    @Mock private DocumentApplicationService documentService;
    @Mock private ChunkRepository            chunkRepository;
    @Mock private VectorStore                vectorStore;
    @InjectMocks private DeleteDocumentUseCase useCase;

    private final TenantId   tenantId   = TenantId.generate();
    private final DocumentId documentId = DocumentId.generate();
    private final UserId     deletedBy  = UserId.generate();

    private DeleteDocumentCommand cmd() {
        return new DeleteDocumentCommand(documentId, tenantId, deletedBy);
    }

    private Chunk indexedChunk(String vectorId) {
        Chunk c = Chunk.create(documentId, tenantId, 0, "text", ChunkMetadata.of("sliding-window"));
        c.assignVectorId(vectorId);
        return c;
    }

    @Test
    void execute_rejectsNullCommand() {
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(null));
    }

    @Test
    void execute_rejectsNullDocumentId() {
        var cmd = new DeleteDocumentCommand(null, tenantId, deletedBy);
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(cmd));
    }

    @Test
    void execute_rejectsNullTenantId() {
        var cmd = new DeleteDocumentCommand(documentId, null, deletedBy);
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(cmd));
    }

    @Test
    void execute_rejectsNullDeletedBy() {
        var cmd = new DeleteDocumentCommand(documentId, tenantId, null);
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(cmd));
    }

    @Test
    void execute_deletesVectorsAndChunksBeforeDocument() {
        when(chunkRepository.findByDocumentId(documentId))
                .thenReturn(List.of(indexedChunk("vector-1"), indexedChunk("vector-2")));

        useCase.execute(cmd());

        verify(vectorStore).deleteByIds(List.of("vector-1", "vector-2"));
        verify(chunkRepository).deleteByDocumentId(documentId);
        verify(documentService).deleteDocument(cmd());
    }

    @Test
    void execute_skipsVectorDeletion_whenNoIndexedChunks() {
        when(chunkRepository.findByDocumentId(documentId)).thenReturn(List.of());

        useCase.execute(cmd());

        verify(vectorStore, never()).deleteByIds(anyList());
        verify(chunkRepository).deleteByDocumentId(documentId);
        verify(documentService).deleteDocument(cmd());
    }
}
