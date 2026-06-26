package com.mudassir.eka.infrastructure.persistence.postgres.adapter;

import com.mudassir.eka.domain.chunk.Chunk;
import com.mudassir.eka.domain.chunk.ChunkId;
import com.mudassir.eka.domain.chunk.ChunkRepository;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.DocumentEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.TenantEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.mapper.ChunkPersistenceMapper;
import com.mudassir.eka.infrastructure.persistence.postgres.repository.ChunkJpaRepository;
import com.mudassir.eka.infrastructure.persistence.postgres.repository.DocumentJpaRepository;
import com.mudassir.eka.infrastructure.persistence.postgres.repository.TenantJpaRepository;
import lombok.RequiredArgsConstructor;
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

        var entities = chunks.stream()
                .map(c -> mapper.toEntity(c, doc, tenant))
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

    @Override
    @Transactional
    public void deleteByDocumentId(DocumentId documentId) {
        chunkJpaRepository.deleteByDocumentId(documentId.value());
    }
}
