package com.mudassir.eka.application.document;

import com.mudassir.eka.application.event.DocumentDeletedEvent;
import com.mudassir.eka.application.event.DocumentRegisteredEvent;
import com.mudassir.eka.application.shared.DomainEventPublisher;
import com.mudassir.eka.application.shared.ResourceNotFoundException;
import com.mudassir.eka.domain.document.Document;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.document.DocumentRepository;
import com.mudassir.eka.domain.shared.PageRequest;
import com.mudassir.eka.domain.shared.PageResult;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DocumentApplicationService {

    private final DocumentRepository    documentRepository;
    private final DomainEventPublisher  eventPublisher;

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
    public Document getDocument(DocumentId id, TenantId tenantId) {
        return documentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", id.value().toString()));
    }

    @Transactional(readOnly = true)
    public PageResult<Document> listDocuments(TenantId tenantId, PageRequest pageRequest) {
        return documentRepository.findByTenantId(tenantId, pageRequest);
    }

    @Transactional(readOnly = true)
    public PageResult<Document> listDocumentsByOwner(UserId ownerId, TenantId tenantId,
                                                      PageRequest pageRequest) {
        return documentRepository.findByOwnerIdAndTenantId(ownerId, tenantId, pageRequest);
    }

    public Document updateMetadata(UpdateDocumentMetadataCommand cmd) {
        Document document = documentRepository.findByIdAndTenantId(cmd.documentId(), cmd.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Document", cmd.documentId().value().toString()));
        document.updateMetadata(cmd.metadata());
        return documentRepository.save(document);
    }

    public void deleteDocument(DeleteDocumentCommand cmd) {
        documentRepository.findByIdAndTenantId(cmd.documentId(), cmd.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Document", cmd.documentId().value().toString()));
        documentRepository.softDelete(cmd.documentId());
        log.info("Document deleted: id={} tenant={} by={}",
                cmd.documentId(), cmd.tenantId(), cmd.deletedBy());
        eventPublisher.publish(new DocumentDeletedEvent(cmd.documentId(), cmd.tenantId(), cmd.deletedBy()));
    }
}
