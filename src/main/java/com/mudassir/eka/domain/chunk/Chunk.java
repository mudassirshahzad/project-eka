package com.mudassir.eka.domain.chunk;

import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.shared.TenantId;

import java.time.Instant;
import java.util.Objects;

public class Chunk {

    private final ChunkId      id;
    private final DocumentId   documentId;
    private final TenantId     tenantId;
    private final int          sequenceNumber;
    private final String       content;
    private final ChunkMetadata metadata;
    private String             vectorId;
    private String             embeddingModel;
    private Integer            embeddingDimension;
    private Instant            embeddedAt;
    private final Instant      createdAt;

    public static Chunk create(
            DocumentId documentId,
            TenantId tenantId,
            int sequenceNumber,
            String content,
            ChunkMetadata metadata
    ) {
        Chunk chunk = new Chunk(ChunkId.generate(), documentId, tenantId, sequenceNumber, content, metadata, Instant.now());
        return chunk;
    }

    public static Chunk reconstitute(
            ChunkId id, DocumentId documentId, TenantId tenantId,
            int sequenceNumber, String content, ChunkMetadata metadata,
            String vectorId, String embeddingModel, Integer embeddingDimension, Instant embeddedAt,
            Instant createdAt
    ) {
        Chunk chunk = new Chunk(id, documentId, tenantId, sequenceNumber, content, metadata, createdAt);
        chunk.vectorId           = vectorId;
        chunk.embeddingModel     = embeddingModel;
        chunk.embeddingDimension = embeddingDimension;
        chunk.embeddedAt         = embeddedAt;
        return chunk;
    }

    private Chunk(ChunkId id, DocumentId documentId, TenantId tenantId,
                  int sequenceNumber, String content, ChunkMetadata metadata, Instant createdAt) {
        this.id             = Objects.requireNonNull(id,          "id");
        this.documentId     = Objects.requireNonNull(documentId,  "documentId");
        this.tenantId       = Objects.requireNonNull(tenantId,    "tenantId");
        this.content        = Objects.requireNonNull(content,     "content");
        this.metadata       = Objects.requireNonNull(metadata,    "metadata");
        this.createdAt      = Objects.requireNonNull(createdAt,   "createdAt");
        this.sequenceNumber = sequenceNumber;
    }

    public void assignVectorId(String vectorId) {
        if (this.vectorId != null) {
            throw new IllegalStateException("vectorId is already assigned for chunk " + id);
        }
        this.vectorId = Objects.requireNonNull(vectorId, "vectorId");
    }

    public void clearVectorId() {
        this.vectorId = null;
    }

    public void assignEmbeddingProvenance(String model, int dimension, Instant embeddedAt) {
        this.embeddingModel     = Objects.requireNonNull(model,      "model");
        this.embeddingDimension = dimension;
        this.embeddedAt         = Objects.requireNonNull(embeddedAt, "embeddedAt");
    }

    public boolean isEmbedded() {
        return embeddingModel != null;
    }

    public ChunkId       getId()             { return id; }
    public DocumentId    getDocumentId()     { return documentId; }
    public TenantId      getTenantId()       { return tenantId; }
    public int           getSequenceNumber() { return sequenceNumber; }
    public String        getContent()        { return content; }
    public ChunkMetadata getMetadata()       { return metadata; }
    public String        getVectorId()            { return vectorId; }
    public String        getEmbeddingModel()      { return embeddingModel; }
    public Integer       getEmbeddingDimension()  { return embeddingDimension; }
    public Instant       getEmbeddedAt()          { return embeddedAt; }
    public Instant       getCreatedAt()           { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Chunk c)) return false;
        return id.equals(c.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
