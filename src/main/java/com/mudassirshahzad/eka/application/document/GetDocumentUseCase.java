package com.mudassirshahzad.eka.application.document;

import com.mudassirshahzad.eka.domain.document.Document;
import com.mudassirshahzad.eka.domain.document.DocumentId;
import com.mudassirshahzad.eka.domain.shared.TenantId;
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
