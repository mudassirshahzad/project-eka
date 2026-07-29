package com.mudassirshahzad.eka.infrastructure.persistence.postgres.mapper;

import com.mudassirshahzad.eka.domain.chunk.Chunk;
import com.mudassirshahzad.eka.domain.chunk.ChunkId;
import com.mudassirshahzad.eka.domain.chunk.ChunkMetadata;
import com.mudassirshahzad.eka.domain.document.DocumentId;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.ChunkEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.DocumentEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.TenantEntity;
import org.springframework.stereotype.Component;

@Component
public class ChunkPersistenceMapper {

    public Chunk toDomain(ChunkEntity e) {
        ChunkMetadata metadata = ChunkMetadata.builder(e.getChunkingStrategy())
                .pageNumber(e.getPageNumber())
                .sectionTitle(e.getSectionTitle())
                .startOffset(e.getStartOffset())
                .endOffset(e.getEndOffset())
                .tokenCount(e.getTokenCount())
                .build();

        return Chunk.reconstitute(
                ChunkId.of(e.getId()),
                DocumentId.of(e.getDocument().getId()),
                TenantId.of(e.getTenant().getId()),
                e.getSequenceNumber(),
                e.getContent(),
                metadata,
                e.getVectorId(),
                e.getEmbeddingModel(),
                e.getEmbeddingDimension(),
                e.getEmbeddedAt(),
                e.getCreatedAt()
        );
    }

    public ChunkEntity toEntity(Chunk d, DocumentEntity document, TenantEntity tenant) {
        ChunkEntity entity = ChunkEntity.builder()
                .document(document)
                .tenant(tenant)
                .sequenceNumber(d.getSequenceNumber())
                .content(d.getContent())
                .pageNumber(d.getMetadata().pageNumber())
                .sectionTitle(d.getMetadata().sectionTitle())
                .startOffset(d.getMetadata().startOffset())
                .endOffset(d.getMetadata().endOffset())
                .tokenCount(d.getMetadata().tokenCount())
                .chunkingStrategy(d.getMetadata().chunkingStrategy())
                .vectorId(d.getVectorId())
                .embeddingModel(d.getEmbeddingModel())
                .embeddingDimension(d.getEmbeddingDimension())
                .embeddedAt(d.getEmbeddedAt())
                .build();
        entity.setId(d.getId().value());
        return entity;
    }
}
