package com.mudassir.eka.infrastructure.retrieval.postgres;

import java.util.UUID;

/**
 * Infrastructure-only DTO for a single row returned by the PostgreSQL FTS query.
 * Never crosses the infrastructure boundary — it is converted to {@code RetrievedChunk}
 * inside {@link PostgresBm25RetrievalAdapter} after score normalization.
 */
record Bm25ResultRow(UUID chunkId, UUID documentId, UUID tenantId, String content, double rawScore) {}
