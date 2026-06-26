package com.mudassir.eka.application.document;

import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.document.DocumentMetadata;
import com.mudassir.eka.domain.shared.TenantId;

public record UpdateDocumentMetadataCommand(
        DocumentId       documentId,
        TenantId         tenantId,
        DocumentMetadata metadata
) {}
