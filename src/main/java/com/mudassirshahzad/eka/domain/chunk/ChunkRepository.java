package com.mudassirshahzad.eka.domain.chunk;

import com.mudassirshahzad.eka.domain.document.DocumentId;

import java.util.List;
import java.util.Optional;

public interface ChunkRepository {

    List<Chunk> saveAll(List<Chunk> chunks);

    Optional<Chunk> findById(ChunkId id);

    List<Chunk> findByIds(List<ChunkId> ids);

    Optional<Chunk> findByVectorId(String vectorId);

    List<Chunk> findByDocumentId(DocumentId documentId);

    /**
     * Chunks that were persisted but never successfully indexed into the vector store — the exact
     * residue of a partial ingestion write (WP-3, ADR OR02).
     *
     * <p>A chunk is assigned its {@code vectorId} only after {@code VectorStore.index} succeeds, so
     * a null {@code vectorId} on a committed row means indexing did not complete. That became a
     * reachable state when ADR HD01 replaced the upload pipeline's one long transaction with short
     * per-step ones: Postgres can commit chunk rows and the Weaviate call can then fail.
     *
     * @param limit maximum rows to return, so one reconciliation pass cannot load an unbounded set
     */
    List<Chunk> findUnindexed(int limit);

    void deleteByDocumentId(DocumentId documentId);
}
