package com.mudassir.eka.infrastructure.persistence.postgres.adapter;

import com.mudassir.eka.domain.document.Document;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.document.DocumentRepository;
import com.mudassir.eka.domain.shared.PageResult;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.DocumentEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.TenantEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.UserEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.mapper.DocumentPersistenceMapper;
import com.mudassir.eka.infrastructure.persistence.postgres.repository.DocumentJpaRepository;
import com.mudassir.eka.infrastructure.persistence.postgres.repository.TenantJpaRepository;
import com.mudassir.eka.infrastructure.persistence.postgres.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DocumentRepositoryAdapter implements DocumentRepository {

    private final DocumentJpaRepository documentJpaRepository;
    private final TenantJpaRepository   tenantJpaRepository;
    private final UserJpaRepository     userJpaRepository;
    private final DocumentPersistenceMapper mapper;

    @Override
    @Transactional
    public Document save(Document domain) {
        Optional<DocumentEntity> existing = documentJpaRepository.findById(domain.getId().value());

        DocumentEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            mapper.updateEntity(entity, domain);
        } else {
            TenantEntity tenant = tenantJpaRepository.getReferenceById(domain.getTenantId().value());
            UserEntity   owner  = userJpaRepository.getReferenceById(domain.getOwnerId().value());
            entity = mapper.toEntity(domain, tenant, owner);
        }

        return mapper.toDomain(documentJpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Document> findById(DocumentId id) {
        return documentJpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Document> findByIdAndTenantId(DocumentId id, TenantId tenantId) {
        TenantEntity tenant = tenantJpaRepository.getReferenceById(tenantId.value());
        return documentJpaRepository.findByIdAndTenant(id.value(), tenant).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Document> findByTenantId(TenantId tenantId,
                                               com.mudassir.eka.domain.shared.PageRequest pageRequest) {
        TenantEntity tenant   = tenantJpaRepository.getReferenceById(tenantId.value());
        Page<DocumentEntity> page = documentJpaRepository.findByTenant(
                tenant,
                PageRequest.of(pageRequest.pageNumber(), pageRequest.pageSize())
        );
        return PageResult.of(
                page.getContent().stream().map(mapper::toDomain).toList(),
                pageRequest.pageNumber(),
                pageRequest.pageSize(),
                page.getTotalElements()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Document> findByOwnerIdAndTenantId(UserId ownerId, TenantId tenantId,
                                                          com.mudassir.eka.domain.shared.PageRequest pageRequest) {
        TenantEntity tenant = tenantJpaRepository.getReferenceById(tenantId.value());
        UserEntity   owner  = userJpaRepository.getReferenceById(ownerId.value());
        Page<DocumentEntity> page = documentJpaRepository.findByOwnerAndTenant(
                owner, tenant,
                PageRequest.of(pageRequest.pageNumber(), pageRequest.pageSize())
        );
        return PageResult.of(
                page.getContent().stream().map(mapper::toDomain).toList(),
                pageRequest.pageNumber(),
                pageRequest.pageSize(),
                page.getTotalElements()
        );
    }

    @Override
    @Transactional
    public void softDelete(DocumentId id) {
        documentJpaRepository.softDeleteById(id.value(), Instant.now());
    }
}
