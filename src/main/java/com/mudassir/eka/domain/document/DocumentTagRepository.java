package com.mudassir.eka.domain.document;

import com.mudassir.eka.domain.shared.TenantId;

import java.util.List;

public interface DocumentTagRepository {

    DocumentTag save(DocumentTag tag);

    List<DocumentTag> findByDocumentId(DocumentId documentId);

    /**
     * Cross-document tag search within a tenant. Used for faceted browsing
     * and Weaviate metadata filter suggestions.
     */
    List<DocumentTag> findByTenantAndTag(TenantId tenantId, String tag);

    List<DocumentTag> findByTenantAndCategory(TenantId tenantId, String category);

    void delete(DocumentTagId id);

    /** Cascading removal — call before deleting the parent document. */
    void deleteByDocumentId(DocumentId documentId);
}
