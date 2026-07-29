package com.mudassirshahzad.eka.application.event;

import com.mudassirshahzad.eka.domain.document.DocumentId;
import com.mudassirshahzad.eka.domain.shared.DomainEvent;
import com.mudassirshahzad.eka.domain.shared.TenantId;

public class DocumentIndexedEvent extends DomainEvent {

    private final DocumentId documentId;
    private final TenantId   tenantId;
    private final int        chunkCount;

    public DocumentIndexedEvent(DocumentId documentId, TenantId tenantId, int chunkCount) {
        super();
        this.documentId = documentId;
        this.tenantId   = tenantId;
        this.chunkCount = chunkCount;
    }

    @Override
    public String getEventType() { return "document.indexed"; }

    public DocumentId getDocumentId() { return documentId; }
    public TenantId   getTenantId()   { return tenantId; }
    public int        getChunkCount() { return chunkCount; }
}
