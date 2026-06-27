package com.mudassir.eka.application.document;

import com.mudassir.eka.domain.chunk.Chunk;
import com.mudassir.eka.domain.chunk.ChunkRepository;
import com.mudassir.eka.domain.chunk.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DeleteDocumentUseCase {

    private final DocumentApplicationService documentService;
    private final ChunkRepository            chunkRepository;
    private final VectorStore                vectorStore;

    public void execute(DeleteDocumentCommand cmd) {
        Objects.requireNonNull(cmd,            "command must not be null");
        Objects.requireNonNull(cmd.documentId(), "documentId must not be null");
        Objects.requireNonNull(cmd.tenantId(),   "tenantId must not be null");
        Objects.requireNonNull(cmd.deletedBy(),  "deletedBy must not be null");

        log.debug("Deleting document: id={} tenant={} by={}",
                cmd.documentId(), cmd.tenantId(), cmd.deletedBy());

        // Remove vectors from Weaviate before touching the DB
        List<Chunk> chunks = chunkRepository.findByDocumentId(cmd.documentId());
        List<String> vectorIds = chunks.stream()
                .filter(c -> c.getVectorId() != null)
                .map(Chunk::getVectorId)
                .toList();
        if (!vectorIds.isEmpty()) {
            vectorStore.deleteByIds(vectorIds);
            log.debug("Removed {} vector(s) from Weaviate for document {}", vectorIds.size(), cmd.documentId());
        }

        // Remove chunks from PostgreSQL
        chunkRepository.deleteByDocumentId(cmd.documentId());

        // Soft-delete document + publish DocumentDeletedEvent
        documentService.deleteDocument(cmd);
    }
}
