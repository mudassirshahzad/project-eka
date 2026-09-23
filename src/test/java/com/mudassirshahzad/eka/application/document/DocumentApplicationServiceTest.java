package com.mudassirshahzad.eka.application.document;

import com.mudassirshahzad.eka.application.shared.DomainEventPublisher;
import com.mudassirshahzad.eka.application.shared.ResourceNotFoundException;
import com.mudassirshahzad.eka.domain.document.ClassificationPolicyPort;
import com.mudassirshahzad.eka.domain.document.Document;
import com.mudassirshahzad.eka.domain.document.DocumentClassification;
import com.mudassirshahzad.eka.domain.document.DocumentId;
import com.mudassirshahzad.eka.domain.document.DocumentMetadata;
import com.mudassirshahzad.eka.domain.document.DocumentRepository;
import com.mudassirshahzad.eka.domain.document.SupportedFormat;
import com.mudassirshahzad.eka.domain.shared.PageRequest;
import com.mudassirshahzad.eka.domain.shared.PageResult;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.domain.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Authorization Filter (P06.2) coverage for {@link DocumentApplicationService} — the REST
 * document-endpoint half of the same {@link ClassificationPolicyPort} enforcement
 * {@code RetrievalServiceTest} covers for the retrieval-pipeline half.
 */
@ExtendWith(MockitoExtension.class)
class DocumentApplicationServiceTest {

    @Mock private DocumentRepository       documentRepository;
    @Mock private DomainEventPublisher     eventPublisher;
    @Mock private ClassificationPolicyPort classificationPolicyPort;
    @InjectMocks private DocumentApplicationService service;

    private final TenantId tenantId   = TenantId.generate();
    private final UserId   ownerId    = UserId.generate();
    private final DocumentId documentId = DocumentId.generate();
    private final Set<UserRole> roles  = Set.of(UserRole.VIEWER);

    private Document sampleDocument(DocumentClassification classification) {
        DocumentMetadata metadata = DocumentMetadata.builder().classification(classification).build();
        return Document.reconstitute(documentId, tenantId, ownerId, "file.txt", SupportedFormat.TXT,
                com.mudassirshahzad.eka.domain.document.DocumentStatus.INDEXED, metadata,
                null, null, 1, null, java.time.Instant.now(), java.time.Instant.now(), null);
    }

    // ── getDocument ───────────────────────────────────────────────────────────

    @Test
    void getDocument_permittedClassification_returnsDocument() {
        Document document = sampleDocument(DocumentClassification.PUBLIC);
        when(documentRepository.findByIdAndTenantId(documentId, tenantId)).thenReturn(Optional.of(document));
        when(classificationPolicyPort.isPermitted(roles, "PUBLIC")).thenReturn(true);

        assertThat(service.getDocument(documentId, tenantId, roles)).isSameAs(document);
    }

    @Test
    void getDocument_deniedClassification_throwsResourceNotFound_notForbidden() {
        Document document = sampleDocument(DocumentClassification.RESTRICTED);
        when(documentRepository.findByIdAndTenantId(documentId, tenantId)).thenReturn(Optional.of(document));
        when(classificationPolicyPort.isPermitted(roles, "RESTRICTED")).thenReturn(false);

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.getDocument(documentId, tenantId, roles));
    }

    @Test
    void getDocument_nonexistentDocument_throwsResourceNotFound_withoutConsultingPolicy() {
        when(documentRepository.findByIdAndTenantId(documentId, tenantId)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.getDocument(documentId, tenantId, roles));

        verify(classificationPolicyPort, never()).isPermitted(any(), any());
    }

    // ── listDocuments / listDocumentsByOwner ────────────────────────────────────

    @Test
    void listDocuments_pushesCallersMaxClearanceLevelIntoTheQuery() {
        PageRequest pageRequest = PageRequest.first(20);
        PageResult<Document> page = PageResult.of(List.of(), 0, 20, 0);
        when(classificationPolicyPort.maxClearanceLevel(roles)).thenReturn(DocumentClassification.INTERNAL.level());
        when(documentRepository.findByTenantId(tenantId, pageRequest, DocumentClassification.INTERNAL.level()))
                .thenReturn(page);

        assertThat(service.listDocuments(tenantId, pageRequest, roles)).isSameAs(page);
    }

    @Test
    void listDocumentsByOwner_pushesCallersMaxClearanceLevelIntoTheQuery() {
        PageRequest pageRequest = PageRequest.first(20);
        PageResult<Document> page = PageResult.of(List.of(), 0, 20, 0);
        when(classificationPolicyPort.maxClearanceLevel(roles)).thenReturn(DocumentClassification.INTERNAL.level());
        when(documentRepository.findByOwnerIdAndTenantId(
                eq(ownerId), eq(tenantId), eq(pageRequest), eq(DocumentClassification.INTERNAL.level())))
                .thenReturn(page);

        assertThat(service.listDocumentsByOwner(ownerId, tenantId, pageRequest, roles)).isSameAs(page);
    }

    // ── deleteDocument ────────────────────────────────────────────────────────

    @Test
    void deleteDocument_permittedClassification_softDeletesAndPublishesEvent() {
        Document document = sampleDocument(DocumentClassification.PUBLIC);
        when(documentRepository.findByIdAndTenantId(documentId, tenantId)).thenReturn(Optional.of(document));
        when(classificationPolicyPort.isPermitted(roles, "PUBLIC")).thenReturn(true);

        service.deleteDocument(new DeleteDocumentCommand(documentId, tenantId, ownerId, roles));

        verify(documentRepository).softDelete(documentId);
    }

    @Test
    void deleteDocument_deniedClassification_throwsResourceNotFound_andNeverDeletes() {
        Document document = sampleDocument(DocumentClassification.CONFIDENTIAL);
        when(documentRepository.findByIdAndTenantId(documentId, tenantId)).thenReturn(Optional.of(document));
        when(classificationPolicyPort.isPermitted(roles, "CONFIDENTIAL")).thenReturn(false);

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.deleteDocument(new DeleteDocumentCommand(documentId, tenantId, ownerId, roles)));

        verify(documentRepository, never()).softDelete(any());
    }
}
