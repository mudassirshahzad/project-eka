package com.mudassirshahzad.eka.application.document;

import com.mudassirshahzad.eka.domain.document.DocumentId;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;

public record DeleteDocumentCommand(
        DocumentId documentId,
        TenantId   tenantId,
        UserId     deletedBy
) {}
