package com.mudassirshahzad.eka.infrastructure.persistence.postgres.adapter;

import com.mudassirshahzad.eka.domain.chunk.Chunk;
import com.mudassirshahzad.eka.domain.chunk.ChunkId;
import com.mudassirshahzad.eka.domain.chunk.ChunkRepository;
import com.mudassirshahzad.eka.domain.document.DocumentId;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.ChunkEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.DocumentEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.TenantEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.mapper.ChunkPersistenceMapper;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.repository.ChunkJpaRepository;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.repository.DocumentJpaRepository;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.repository.TenantJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChunkRepositoryAdapter implements ChunkRepository {

    private final ChunkJpaRepository    chunkJpaRepository;
    private final DocumentJpaRepository documentJpaRepository;
    private final TenantJpaRepository   tenantJpaRepository;
    private final ChunkPersistenceMapper mapper;

    @Override
    @Transactional
    public List<Chunk> saveAll(List<Chunk> chunks) {
        if (chunks.isEmpty()) return List.of();

        Chunk first          = chunks.getFirst();
        DocumentEntity doc   = documentJpaRepository.getReferenceById(first.getDocumentId().value());
        TenantEntity   tenant = tenantJpaRepository.getReferenceById(first.getTenantId().value());

        // Re-saving an already-persisted chunk is a normal part of ingestion, not an edge case:
        // ChunkApplicationService persists chunks first, then DocumentIndexingService saves them
        // again to record the vectorId that indexing produced. Mapping such a chunk to a *fresh*
        // detached entity loses createdAt (assigned by @PrePersist on the original insert), and
        // because created_at is updatable=false the merged instance comes back with a null value
        // that Chunk.reconstitute then rejects. Loading the existing row and updating only the
        // fields indexing actually changes mirrors what UserRepositoryAdapter.save already does.
        var entities = chunks.stream()
                .map(c -> chunkJpaRepository.findById(c.getId().value())
                        .map(existing -> applyIndexingState(existing, c))
                        .orElseGet(() -> mapper.toEntity(c, doc, tenant)))
                .toList();

        return chunkJpaRepository.saveAll(entities).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Chunk> findById(ChunkId id) {
        return chunkJpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Chunk> findByIds(List<ChunkId> ids) {
        List<UUID> uuids = ids.stream().map(ChunkId::value).toList();
        return chunkJpaRepository.findByIdIn(uuids).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Chunk> findByVectorId(String vectorId) {
        return chunkJpaRepository.findByVectorId(vectorId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Chunk> findByDocumentId(DocumentId documentId) {
        DocumentEntity doc = documentJpaRepository.getReferenceById(documentId.value());
        return chunkJpaRepository.findByDocument(doc).stream()
                .map(mapper::toDomain)
                .toList();
    }

    /**
     * Copies the only chunk state that changes after creation — what indexing assigns or clears.
     * Content, sequence, and metadata are immutable once a chunk exists, so they are deliberately
     * not copied: doing so would let a stale in-memory chunk silently overwrite a stored one.
     */
    private ChunkEntity applyIndexingState(ChunkEntity entity, Chunk chunk) {
        entity.setVectorId(chunk.getVectorId());
        entity.setEmbeddingModel(chunk.getEmbeddingModel());
        entity.setEmbeddingDimension(chunk.getEmbeddingDimension());
        entity.setEmbeddedAt(chunk.getEmbeddedAt());
        return entity;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Chunk> findUnindexed(int limit) {
        if (limit <= 0) return List.of();
        return chunkJpaRepository.findUnindexed(PageRequest.of(0, limit)).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteByDocumentId(DocumentId documentId) {
        chunkJpaRepository.deleteByDocumentId(documentId.value());
    }
}
