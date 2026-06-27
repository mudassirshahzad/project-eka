package com.mudassir.eka.domain.retrieval.model;

import com.mudassir.eka.domain.chunk.ChunkId;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.shared.TenantId;

/**
 * A single result item returned by the retrieval layer.
 *
 * @param chunkId    identity of the source chunk
 * @param documentId identity of the source document
 * @param tenantId   tenant that owns this chunk
 * @param content    raw text content of the chunk
 * @param score      normalized relevance score in the range {@code [0.0, 1.0]}.
 *                   Retrieval adapters are responsible for normalizing their native
 *                   scores (cosine similarity, BM25, RRF) to this range before
 *                   constructing a {@code RetrievedChunk}. Higher values indicate
 *                   greater relevance to the query.
 * @param rank       zero-based position of this item in the <em>raw retrieval output</em>
 *                   returned by the underlying engine, before any post-filtering or
 *                   re-ranking. Items that do not survive post-filtering are excluded
 *                   from the result; surviving items may therefore have non-consecutive
 *                   ranks. This invariant is required for correct Reciprocal Rank Fusion.
 */
public record RetrievedChunk(
        ChunkId chunkId,
        DocumentId documentId,
        TenantId tenantId,
        String content,
        double score,
        int rank) {

    public RetrievedChunk {
        if (chunkId == null)    throw new IllegalArgumentException("chunkId must not be null");
        if (documentId == null) throw new IllegalArgumentException("documentId must not be null");
        if (tenantId == null)   throw new IllegalArgumentException("tenantId must not be null");
        if (content == null)    throw new IllegalArgumentException("content must not be null");
        if (rank < 0)           throw new IllegalArgumentException("rank must be >= 0 but was " + rank);
    }
}
