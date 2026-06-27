package com.mudassir.eka.domain.document;

import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;

import java.time.Instant;
import java.util.Objects;

public class Document {

    private final DocumentId id;
    private final TenantId   tenantId;
    private final UserId     ownerId;
    private final String     filename;
    private final SupportedFormat format;
    private DocumentStatus   status;
    private DocumentMetadata metadata;
    private String           rawContentPath;
    private String           parsedTextPath;
    private int              chunkCount;
    private String           ingestionError;
    private final Instant    createdAt;
    private Instant          updatedAt;
    private Instant          deletedAt;

    public static Document create(
            TenantId tenantId,
            UserId ownerId,
            String filename,
            SupportedFormat format,
            DocumentMetadata metadata
    ) {
        Document doc = new Document(DocumentId.generate(), tenantId, ownerId, filename, format, Instant.now());
        doc.status     = DocumentStatus.PENDING;
        doc.metadata   = Objects.requireNonNull(metadata, "metadata");
        doc.chunkCount = 0;
        doc.updatedAt  = doc.createdAt;
        return doc;
    }

    public static Document reconstitute(
            DocumentId id, TenantId tenantId, UserId ownerId,
            String filename, SupportedFormat format, DocumentStatus status,
            DocumentMetadata metadata, String rawContentPath, String parsedTextPath,
            int chunkCount, String ingestionError,
            Instant createdAt, Instant updatedAt, Instant deletedAt
    ) {
        Document doc = new Document(id, tenantId, ownerId, filename, format, createdAt);
        doc.status         = status;
        doc.metadata       = metadata;
        doc.rawContentPath = rawContentPath;
        doc.parsedTextPath = parsedTextPath;
        doc.chunkCount     = chunkCount;
        doc.ingestionError = ingestionError;
        doc.updatedAt      = updatedAt;
        doc.deletedAt      = deletedAt;
        return doc;
    }

    private Document(DocumentId id, TenantId tenantId, UserId ownerId,
                     String filename, SupportedFormat format, Instant createdAt) {
        this.id        = Objects.requireNonNull(id,        "id");
        this.tenantId  = Objects.requireNonNull(tenantId,  "tenantId");
        this.ownerId   = Objects.requireNonNull(ownerId,   "ownerId");
        this.filename  = Objects.requireNonNull(filename,  "filename");
        this.format    = Objects.requireNonNull(format,    "format");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    // ── State machine ─────────────────────────────────────────────

    public void startParsing() {
        requireStatus(DocumentStatus.PENDING);
        transition(DocumentStatus.PARSING);
    }

    public void startChunking() {
        requireStatus(DocumentStatus.PARSING);
        transition(DocumentStatus.CHUNKING);
    }

    public void startEmbedding() {
        requireStatus(DocumentStatus.CHUNKING);
        transition(DocumentStatus.EMBEDDING);
    }

    public void markIndexed(int chunkCount) {
        requireStatus(DocumentStatus.EMBEDDING);
        this.chunkCount = chunkCount;
        transition(DocumentStatus.INDEXED);
    }

    public void markFailed(String error) {
        this.ingestionError = error;
        transition(DocumentStatus.FAILED);
    }

    public void assignContentPath(String path) {
        this.rawContentPath = Objects.requireNonNull(path);
        this.updatedAt      = Instant.now();
    }

    public void assignParsedTextPath(String path) {
        this.parsedTextPath = Objects.requireNonNull(path);
        this.updatedAt      = Instant.now();
    }

    public void updateMetadata(DocumentMetadata metadata) {
        this.metadata  = Objects.requireNonNull(metadata);
        this.updatedAt = Instant.now();
    }

    public void updateChunkCount(int count) {
        if (count < 0) throw new IllegalArgumentException("chunkCount must not be negative");
        this.chunkCount = count;
        this.updatedAt  = Instant.now();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted()     { return deletedAt != null; }
    public boolean isQueryable()   { return status == DocumentStatus.INDEXED && !isDeleted(); }

    // ── Getters ───────────────────────────────────────────────────

    public DocumentId        getId()              { return id; }
    public TenantId          getTenantId()        { return tenantId; }
    public UserId            getOwnerId()         { return ownerId; }
    public String            getFilename()        { return filename; }
    public SupportedFormat   getFormat()          { return format; }
    public DocumentStatus    getStatus()          { return status; }
    public DocumentMetadata  getMetadata()        { return metadata; }
    public String            getRawContentPath()  { return rawContentPath; }
    public String            getParsedTextPath()  { return parsedTextPath; }
    public int               getChunkCount()      { return chunkCount; }
    public String            getIngestionError()  { return ingestionError; }
    public Instant           getCreatedAt()       { return createdAt; }
    public Instant           getUpdatedAt()       { return updatedAt; }
    public Instant           getDeletedAt()       { return deletedAt; }

    // ── Helpers ───────────────────────────────────────────────────

    private void requireStatus(DocumentStatus expected) {
        if (this.status != expected) {
            throw new IllegalStateException(
                "Document %s: expected status %s but was %s".formatted(id, expected, status)
            );
        }
    }

    private void transition(DocumentStatus next) {
        this.status    = next;
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Document d)) return false;
        return id.equals(d.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
