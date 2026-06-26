package com.mudassir.eka.application.document;

import com.mudassir.eka.domain.document.DocumentMetadata;
import com.mudassir.eka.domain.document.SupportedFormat;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;

public record UploadDocumentCommand(
        TenantId         tenantId,
        UserId           ownerId,
        String           filename,
        SupportedFormat  format,
        DocumentMetadata metadata,
        byte[]           content
) {}
