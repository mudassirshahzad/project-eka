package com.mudassirshahzad.eka.application.document;

import com.mudassirshahzad.eka.domain.document.DocumentId;
import com.mudassirshahzad.eka.domain.document.DocumentMetadata;
import com.mudassirshahzad.eka.domain.shared.TenantId;

public record UpdateDocumentMetadataCommand(
        DocumentId       documentId,
        TenantId         tenantId,
        DocumentMetadata metadata
) {}
