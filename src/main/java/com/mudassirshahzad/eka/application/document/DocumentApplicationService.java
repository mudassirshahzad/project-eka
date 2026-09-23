package com.mudassirshahzad.eka.application.document;

import com.mudassirshahzad.eka.application.event.DocumentDeletedEvent;
import com.mudassirshahzad.eka.application.event.DocumentRegisteredEvent;
import com.mudassirshahzad.eka.application.shared.DomainEventPublisher;
import com.mudassirshahzad.eka.application.shared.ResourceNotFoundException;
import com.mudassirshahzad.eka.domain.document.ClassificationPolicyPort;
import com.mudassirshahzad.eka.domain.document.Document;
import com.mudassirshahzad.eka.domain.document.DocumentId;
import com.mudassirshahzad.eka.domain.document.DocumentRepository;
import com.mudassirshahzad.eka.domain.shared.PageRequest;
import com.mudassirshahzad.eka.domain.shared.PageResult;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Authorization Filter note (P06.2): {@code getDocument}/{@code listDocuments}/
 * {@code listDocumentsByOwner}/{@code deleteDocument} all resolve classification clearance via
 * {@link ClassificationPolicyPort} — the same port {@code RetrievalService} calls for the
 * retrieval-pipeline path, so the two access paths can't drift into different authorization
 * rules. A document above the caller's clearance resolves to {@link ResourceNotFoundException},
 * identical to a genuinely nonexistent or wrong-tenant document (ADR OW01's anti-enumeration
 * precedent, extended to classification) — never a distinct 403, and its metadata never appears
 * in a response the caller isn't cleared to receive.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DocumentApplicationService {

    private final DocumentRepository       documentRepository;
    private final DomainEventPublisher     eventPublisher;
    private final ClassificationPolicyPort classificationPolicyPort;

    public Document registerDocument(RegisterDocumentCommand cmd) {
        Document document = Document.create(
                cmd.tenantId(), cmd.ownerId(), cmd.filename(), cmd.format(), cmd.metadata());
        Document saved = documentRepository.save(document);
        log.info("Document registered: id={} filename={} tenant={}",
                saved.getId(), saved.getFilename(), saved.getTenantId());
        eventPublisher.publish(new DocumentRegisteredEvent(
                saved.getId(), saved.getTenantId(), saved.getOwnerId(),
                saved.getFilename(), saved.getFormat()));
        return saved;
    }

    @Transactional(readOnly = true)
    public Document getDocument(DocumentId id, TenantId tenantId, Set<UserRole> roles) {
        Document document = documentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", id.value().toString()));
        requireClassificationClearance(document, roles, id);
        return document;
    }

    @Transactional(readOnly = true)
    public PageResult<Document> listDocuments(TenantId tenantId, PageRequest pageRequest, Set<UserRole> roles) {
        return documentRepository.findByTenantId(tenantId, pageRequest, classificationPolicyPort.maxClearanceLevel(roles));
    }

    @Transactional(readOnly = true)
    public PageResult<Document> listDocumentsByOwner(UserId ownerId, TenantId tenantId,
                                                      PageRequest pageRequest, Set<UserRole> roles) {
        return documentRepository.findByOwnerIdAndTenantId(
                ownerId, tenantId, pageRequest, classificationPolicyPort.maxClearanceLevel(roles));
    }

    public Document updateDocument(Document document) {
        return documentRepository.save(document);
    }

    public Document updateMetadata(UpdateDocumentMetadataCommand cmd) {
        Document document = documentRepository.findByIdAndTenantId(cmd.documentId(), cmd.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Document", cmd.documentId().value().toString()));
        document.updateMetadata(cmd.metadata());
        return documentRepository.save(document);
    }

    public void deleteDocument(DeleteDocumentCommand cmd) {
        Document document = documentRepository.findByIdAndTenantId(cmd.documentId(), cmd.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Document", cmd.documentId().value().toString()));
        requireClassificationClearance(document, cmd.roles(), cmd.documentId());
        documentRepository.softDelete(cmd.documentId());
        log.info("Document deleted: id={} tenant={} by={}",
                cmd.documentId(), cmd.tenantId(), cmd.deletedBy());
        eventPublisher.publish(new DocumentDeletedEvent(cmd.documentId(), cmd.tenantId(), cmd.deletedBy()));
    }

    private void requireClassificationClearance(Document document, Set<UserRole> roles, DocumentId id) {
        String storedClassification = document.getMetadata() != null && document.getMetadata().classification() != null
                ? document.getMetadata().classification().name()
                : null;
        if (!classificationPolicyPort.isPermitted(roles, storedClassification)) {
            throw new ResourceNotFoundException("Document", id.value().toString());
        }
    }
}
