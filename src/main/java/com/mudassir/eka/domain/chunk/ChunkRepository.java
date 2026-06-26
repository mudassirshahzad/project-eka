package com.mudassir.eka.domain.chunk;

import com.mudassir.eka.domain.document.DocumentId;

import java.util.List;
import java.util.Optional;

public interface ChunkRepository {

    List<Chunk> saveAll(List<Chunk> chunks);

    Optional<Chunk> findById(ChunkId id);

    List<Chunk> findByIds(List<ChunkId> ids);

    Optional<Chunk> findByVectorId(String vectorId);

    List<Chunk> findByDocumentId(DocumentId documentId);

    void deleteByDocumentId(DocumentId documentId);
}
