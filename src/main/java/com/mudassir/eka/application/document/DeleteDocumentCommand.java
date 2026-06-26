package com.mudassir.eka.application.document;

import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;

public record DeleteDocumentCommand(
        DocumentId documentId,
        TenantId   tenantId,
        UserId     deletedBy
) {}
