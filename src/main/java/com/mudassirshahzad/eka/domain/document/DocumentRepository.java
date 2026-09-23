package com.mudassirshahzad.eka.domain.document;

import com.mudassirshahzad.eka.domain.shared.PageRequest;
import com.mudassirshahzad.eka.domain.shared.PageResult;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository {

    Document save(Document document);

    Optional<Document> findById(DocumentId id);

    Optional<Document> findByIdAndTenantId(DocumentId id, TenantId tenantId);

    /**
     * Batch lookup by id, in no particular guaranteed order, used by the Authorization Filter
     * (P06.2, {@code RetrievalService}) to resolve the classification of every document behind a
     * retrieved chunk in one query instead of one per chunk. IDs with no matching document are
     * simply absent from the result — never an error.
     */
    List<Document> findByIds(List<DocumentId> ids);

    /**
     * @param maxClassificationLevel Authorization Filter clearance bound (P06.2, see
     *                               {@link ClassificationPolicyPort#maxClearanceLevel}) — only
     *                               documents at or below this {@link DocumentClassification#level()}
     *                               are included. Enforced in the query itself, not after
     *                               pagination, so {@code totalElements}/page size stay correct.
     */
    PageResult<Document> findByTenantId(TenantId tenantId, PageRequest pageRequest, int maxClassificationLevel);

    PageResult<Document> findByOwnerIdAndTenantId(
            UserId ownerId, TenantId tenantId, PageRequest pageRequest, int maxClassificationLevel);

    void softDelete(DocumentId id);

    /**
     * Count of documents with no classification set — used by the P06.2 backfill-verification
     * startup check, never by an authorization decision itself (which always fails closed on
     * {@code null} directly, without needing a count).
     */
    long countUnclassified();
}
