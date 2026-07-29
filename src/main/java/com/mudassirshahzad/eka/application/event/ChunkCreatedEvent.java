package com.mudassirshahzad.eka.application.event;

import com.mudassirshahzad.eka.domain.chunk.ChunkId;
import com.mudassirshahzad.eka.domain.document.DocumentId;
import com.mudassirshahzad.eka.domain.shared.DomainEvent;
import com.mudassirshahzad.eka.domain.shared.TenantId;

public class ChunkCreatedEvent extends DomainEvent {

    private final ChunkId    chunkId;
    private final DocumentId documentId;
    private final TenantId   tenantId;
    private final int        sequenceNumber;

    public ChunkCreatedEvent(ChunkId chunkId, DocumentId documentId,
                             TenantId tenantId, int sequenceNumber) {
        super();
        this.chunkId        = chunkId;
        this.documentId     = documentId;
        this.tenantId       = tenantId;
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public String getEventType() { return "chunk.created"; }

    public ChunkId    getChunkId()        { return chunkId; }
    public DocumentId getDocumentId()     { return documentId; }
    public TenantId   getTenantId()       { return tenantId; }
    public int        getSequenceNumber() { return sequenceNumber; }
}
