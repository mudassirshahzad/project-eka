package com.mudassir.eka.infrastructure.persistence.postgres.mapper;

import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.document.DocumentTag;
import com.mudassir.eka.domain.document.DocumentTagId;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.DocumentEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.DocumentTagEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.TenantEntity;
import org.springframework.stereotype.Component;

@Component
public class DocumentTagPersistenceMapper {

    public DocumentTag toDomain(DocumentTagEntity e) {
        return new DocumentTag(
                DocumentTagId.of(e.getId()),
                DocumentId.of(e.getDocument().getId()),
                TenantId.of(e.getTenant().getId()),
                e.getTag(),
                e.getCategory(),
                UserId.of(e.getCreatedBy()),
                e.getCreatedAt()
        );
    }

    public DocumentTagEntity toEntity(DocumentTag d, DocumentEntity document, TenantEntity tenant) {
        DocumentTagEntity entity = DocumentTagEntity.builder()
                .document(document)
                .tenant(tenant)
                .tag(d.tag())
                .category(d.category())
                .createdBy(d.createdBy().value())
                .build();
        entity.setId(d.id().value());
        return entity;
    }
}
