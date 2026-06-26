package com.mudassir.eka.application.event;

import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.document.SupportedFormat;
import com.mudassir.eka.domain.shared.DomainEvent;
import com.mudassir.eka.domain.shared.TenantId;

public class DocumentParsedEvent extends DomainEvent {

    private final DocumentId    documentId;
    private final TenantId      tenantId;
    private final SupportedFormat detectedFormat;
    private final String        parsedTextPath;

    public DocumentParsedEvent(DocumentId documentId, TenantId tenantId,
                                SupportedFormat detectedFormat, String parsedTextPath) {
        super();
        this.documentId     = documentId;
        this.tenantId       = tenantId;
        this.detectedFormat = detectedFormat;
        this.parsedTextPath = parsedTextPath;
    }

    @Override
    public String getEventType() { return "document.parsed"; }

    public DocumentId     getDocumentId()     { return documentId; }
    public TenantId       getTenantId()       { return tenantId; }
    public SupportedFormat getDetectedFormat() { return detectedFormat; }
    public String         getParsedTextPath() { return parsedTextPath; }
}
