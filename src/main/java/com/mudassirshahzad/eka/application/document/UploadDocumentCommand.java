package com.mudassirshahzad.eka.application.document;

import com.mudassirshahzad.eka.domain.document.DocumentMetadata;
import com.mudassirshahzad.eka.domain.document.SupportedFormat;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;

public record UploadDocumentCommand(
        TenantId         tenantId,
        UserId           ownerId,
        String           filename,
        SupportedFormat  format,
        DocumentMetadata metadata,
        byte[]           content
) {}
