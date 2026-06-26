package com.mudassir.eka.domain.document;

import com.mudassir.eka.domain.shared.PageRequest;
import com.mudassir.eka.domain.shared.PageResult;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;

import java.util.Optional;

public interface DocumentRepository {

    Document save(Document document);

    Optional<Document> findById(DocumentId id);

    Optional<Document> findByIdAndTenantId(DocumentId id, TenantId tenantId);

    PageResult<Document> findByTenantId(TenantId tenantId, PageRequest pageRequest);

    PageResult<Document> findByOwnerIdAndTenantId(UserId ownerId, TenantId tenantId, PageRequest pageRequest);

    void softDelete(DocumentId id);
}
