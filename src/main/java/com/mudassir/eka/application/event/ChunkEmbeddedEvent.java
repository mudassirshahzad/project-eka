package com.mudassir.eka.application.event;

import com.mudassir.eka.domain.chunk.ChunkId;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.shared.DomainEvent;
import com.mudassir.eka.domain.shared.TenantId;

public class ChunkEmbeddedEvent extends DomainEvent {

    private final ChunkId    chunkId;
    private final DocumentId documentId;
    private final TenantId   tenantId;
    private final String     embeddingModel;
    private final int        embeddingDimension;

    public ChunkEmbeddedEvent(ChunkId chunkId, DocumentId documentId, TenantId tenantId,
                               String embeddingModel, int embeddingDimension) {
        super();
        this.chunkId            = chunkId;
        this.documentId         = documentId;
        this.tenantId           = tenantId;
        this.embeddingModel     = embeddingModel;
        this.embeddingDimension = embeddingDimension;
    }

    @Override
    public String getEventType() { return "chunk.embedded"; }

    public ChunkId    getChunkId()            { return chunkId; }
    public DocumentId getDocumentId()         { return documentId; }
    public TenantId   getTenantId()           { return tenantId; }
    public String     getEmbeddingModel()     { return embeddingModel; }
    public int        getEmbeddingDimension() { return embeddingDimension; }
}
