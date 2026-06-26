package com.mudassir.eka.application.document;

import com.mudassir.eka.domain.document.Document;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.shared.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetDocumentUseCase {

    private final DocumentApplicationService documentService;

    public Document execute(DocumentId id, TenantId tenantId) {
        Objects.requireNonNull(id, "documentId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return documentService.getDocument(id, tenantId);
    }
}
