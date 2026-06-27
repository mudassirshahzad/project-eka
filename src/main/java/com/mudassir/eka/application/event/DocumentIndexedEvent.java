package com.mudassir.eka.application.event;

import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.shared.DomainEvent;
import com.mudassir.eka.domain.shared.TenantId;

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
