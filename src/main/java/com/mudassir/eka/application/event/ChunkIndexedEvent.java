package com.mudassir.eka.application.event;

import com.mudassir.eka.domain.chunk.ChunkId;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.shared.DomainEvent;
import com.mudassir.eka.domain.shared.TenantId;

public class ChunkIndexedEvent extends DomainEvent {

    private final ChunkId    chunkId;
    private final DocumentId documentId;
    private final TenantId   tenantId;
    private final String     vectorId;

    public ChunkIndexedEvent(ChunkId chunkId, DocumentId documentId,
                              TenantId tenantId, String vectorId) {
        super();
        this.chunkId    = chunkId;
        this.documentId = documentId;
        this.tenantId   = tenantId;
        this.vectorId   = vectorId;
    }

    @Override
    public String getEventType() { return "chunk.indexed"; }

    public ChunkId    getChunkId()    { return chunkId; }
    public DocumentId getDocumentId() { return documentId; }
    public TenantId   getTenantId()   { return tenantId; }
    public String     getVectorId()   { return vectorId; }
}
