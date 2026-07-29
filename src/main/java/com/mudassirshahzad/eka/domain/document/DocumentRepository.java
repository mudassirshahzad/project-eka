package com.mudassirshahzad.eka.domain.document;

import com.mudassirshahzad.eka.domain.shared.PageRequest;
import com.mudassirshahzad.eka.domain.shared.PageResult;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;

import java.util.Optional;

public interface DocumentRepository {

    Document save(Document document);

    Optional<Document> findById(DocumentId id);

    Optional<Document> findByIdAndTenantId(DocumentId id, TenantId tenantId);

    PageResult<Document> findByTenantId(TenantId tenantId, PageRequest pageRequest);

    PageResult<Document> findByOwnerIdAndTenantId(UserId ownerId, TenantId tenantId, PageRequest pageRequest);

    void softDelete(DocumentId id);
}
