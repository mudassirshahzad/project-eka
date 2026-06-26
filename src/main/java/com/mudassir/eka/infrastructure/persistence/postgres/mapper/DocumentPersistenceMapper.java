package com.mudassir.eka.infrastructure.persistence.postgres.mapper;

import com.mudassir.eka.domain.document.Document;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.document.DocumentMetadata;
import com.mudassir.eka.domain.document.DocumentStatus;
import com.mudassir.eka.domain.document.SupportedFormat;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.DocumentEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.TenantEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DocumentPersistenceMapper {

    public Document toDomain(DocumentEntity e) {
        return Document.reconstitute(
                DocumentId.of(e.getId()),
                TenantId.of(e.getTenant().getId()),
                UserId.of(e.getOwner().getId()),
                e.getFilename(),
                SupportedFormat.valueOf(e.getFormat()),
                DocumentStatus.valueOf(e.getStatus()),
                toMetadata(e),
                e.getRawContentPath(),
                e.getChunkCount(),
                e.getIngestionError(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getDeletedAt()
        );
    }

    public DocumentEntity toEntity(Document d, TenantEntity tenant, UserEntity owner) {
        DocumentEntity entity = DocumentEntity.builder()
                .tenant(tenant)
                .owner(owner)
                .filename(d.getFilename())
                .format(d.getFormat().name())
                .status(d.getStatus().name())
                .title(d.getMetadata().title())
                .author(d.getMetadata().author())
                .description(d.getMetadata().description())
                .department(d.getMetadata().department())
                .classification(d.getMetadata().classification())
                .tags(toTagArray(d.getMetadata().tags()))
                .rawContentPath(d.getRawContentPath())
                .chunkCount(d.getChunkCount())
                .ingestionError(d.getIngestionError())
                .deletedAt(d.getDeletedAt())
                .build();
        entity.setId(d.getId().value());
        return entity;
    }

    public void updateEntity(DocumentEntity entity, Document d) {
        entity.setStatus(d.getStatus().name());
        entity.setTitle(d.getMetadata().title());
        entity.setAuthor(d.getMetadata().author());
        entity.setDescription(d.getMetadata().description());
        entity.setDepartment(d.getMetadata().department());
        entity.setClassification(d.getMetadata().classification());
        entity.setTags(toTagArray(d.getMetadata().tags()));
        entity.setRawContentPath(d.getRawContentPath());
        entity.setChunkCount(d.getChunkCount());
        entity.setIngestionError(d.getIngestionError());
        entity.setDeletedAt(d.getDeletedAt());
    }

    private DocumentMetadata toMetadata(DocumentEntity e) {
        Set<String> tags = e.getTags() != null
                ? Arrays.stream(e.getTags()).collect(Collectors.toSet())
                : Set.of();
        return DocumentMetadata.builder()
                .title(e.getTitle())
                .author(e.getAuthor())
                .description(e.getDescription())
                .department(e.getDepartment())
                .classification(e.getClassification())
                .tags(tags)
                .build();
    }

    private String[] toTagArray(Set<String> tags) {
        return tags == null || tags.isEmpty() ? new String[0] : tags.toArray(String[]::new);
    }
}
