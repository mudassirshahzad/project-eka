package com.mudassir.eka.application.event;

import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.document.SupportedFormat;
import com.mudassir.eka.domain.shared.DomainEvent;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;

public class DocumentRegisteredEvent extends DomainEvent {

    private final DocumentId    documentId;
    private final TenantId      tenantId;
    private final UserId        ownerId;
    private final String        filename;
    private final SupportedFormat format;

    public DocumentRegisteredEvent(DocumentId documentId, TenantId tenantId,
                                    UserId ownerId, String filename, SupportedFormat format) {
        super();
        this.documentId = documentId;
        this.tenantId   = tenantId;
        this.ownerId    = ownerId;
        this.filename   = filename;
        this.format     = format;
    }

    @Override
    public String getEventType() { return "document.registered"; }

    public DocumentId     getDocumentId() { return documentId; }
    public TenantId       getTenantId()   { return tenantId; }
    public UserId         getOwnerId()    { return ownerId; }
    public String         getFilename()   { return filename; }
    public SupportedFormat getFormat()   { return format; }
}
