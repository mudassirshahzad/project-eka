package com.mudassir.eka.infrastructure.persistence.postgres.adapter;

import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.document.DocumentTag;
import com.mudassir.eka.domain.document.DocumentTagId;
import com.mudassir.eka.domain.document.DocumentTagRepository;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.DocumentEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.TenantEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.mapper.DocumentTagPersistenceMapper;
import com.mudassir.eka.infrastructure.persistence.postgres.repository.DocumentJpaRepository;
import com.mudassir.eka.infrastructure.persistence.postgres.repository.DocumentTagJpaRepository;
import com.mudassir.eka.infrastructure.persistence.postgres.repository.TenantJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DocumentTagRepositoryAdapter implements DocumentTagRepository {

    private final DocumentTagJpaRepository     documentTagJpaRepository;
    private final DocumentJpaRepository        documentJpaRepository;
    private final TenantJpaRepository          tenantJpaRepository;
    private final DocumentTagPersistenceMapper mapper;

    @Override
    @Transactional
    public DocumentTag save(DocumentTag tag) {
        DocumentEntity document = documentJpaRepository.getReferenceById(tag.documentId().value());
        TenantEntity   tenant   = tenantJpaRepository.getReferenceById(tag.tenantId().value());
        return mapper.toDomain(documentTagJpaRepository.save(mapper.toEntity(tag, document, tenant)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentTag> findByDocumentId(DocumentId documentId) {
        DocumentEntity document = documentJpaRepository.getReferenceById(documentId.value());
        return documentTagJpaRepository.findByDocument(document)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentTag> findByTenantAndTag(TenantId tenantId, String tag) {
        TenantEntity tenant = tenantJpaRepository.getReferenceById(tenantId.value());
        return documentTagJpaRepository.findByTenantAndTagIgnoreCase(tenant, tag)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentTag> findByTenantAndCategory(TenantId tenantId, String category) {
        TenantEntity tenant = tenantJpaRepository.getReferenceById(tenantId.value());
        return documentTagJpaRepository.findByTenantAndCategory(tenant, category)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void delete(DocumentTagId id) {
        documentTagJpaRepository.deleteById(id.value());
    }

    @Override
    @Transactional
    public void deleteByDocumentId(DocumentId documentId) {
        DocumentEntity document = documentJpaRepository.getReferenceById(documentId.value());
        documentTagJpaRepository.deleteByDocument(document);
    }
}
