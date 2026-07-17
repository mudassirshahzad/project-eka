package com.mudassir.eka.domain.retrieval.model;

import com.mudassir.eka.domain.chunk.ChunkId;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.shared.TenantId;

/**
 * A single chunk as it appears in the assembled context, ready for Prompt Builder consumption.
 *
 * <p>Carries the full citation identity ({@link ChunkId}, {@link DocumentId}, {@link TenantId})
 * alongside the raw text content and the relevance score from the retrieval layer.
 * The {@code position} field records the chunk's zero-based index in the final assembled list —
 * this is intentionally distinct from {@link RetrievedChunk#rank()}, which records its rank in
 * the raw retrieval output. Prompt Builder and future Citation Generation depend on
 * {@code position} to reference chunks in order without re-reading retrieval internals.
 *
 * @param chunkId    identity of the source chunk (for citation generation)
 * @param documentId identity of the source document (for citation generation)
 * @param tenantId   tenant that owns this chunk (for audit and isolation)
 * @param content    raw text content — never rewritten or merged by this layer
 * @param score      relevance score inherited from {@link RetrievedChunk#score()} in {@code [0.0, 1.0]}
 * @param position   zero-based index in the assembled context list (0 = most relevant)
 */
public record AssembledChunk(
        ChunkId    chunkId,
        DocumentId documentId,
        TenantId   tenantId,
        String     content,
        double     score,
        int        position) {

    public AssembledChunk {
        if (chunkId    == null) throw new IllegalArgumentException("chunkId must not be null");
        if (documentId == null) throw new IllegalArgumentException("documentId must not be null");
        if (tenantId   == null) throw new IllegalArgumentException("tenantId must not be null");
        if (content    == null) throw new IllegalArgumentException("content must not be null");
        if (position   <  0)   throw new IllegalArgumentException("position must be >= 0 but was " + position);
    }
}
