package com.mudassir.eka.infrastructure.retrieval.weaviate;

import com.mudassir.eka.domain.chunk.Chunk;
import com.mudassir.eka.domain.chunk.VectorSearchResult;
import com.mudassir.eka.domain.retrieval.model.RetrievedChunk;
import org.springframework.stereotype.Component;

@Component
class RetrievedChunkMapper {

    RetrievedChunk toRetrievedChunk(VectorSearchResult raw, Chunk chunk, int rank) {
        return new RetrievedChunk(
                chunk.getId(),
                chunk.getDocumentId(),
                chunk.getTenantId(),
                raw.content(),
                clampToUnitRange(raw.score()),
                rank);
    }

    /**
     * Defensive clamp to {@code [0.0, 1.0]} for Weaviate certainty scores.
     *
     * <p><strong>This is not a normalization transform.</strong> Weaviate certainty
     * (cosine similarity remapped to {@code [0, 1]} by Spring AI's Weaviate adapter)
     * is already within the target range under normal operation. The clamp guards
     * against floating-point edge cases and implementation drift — it does not
     * convert an arbitrary score range into {@code [0, 1]}.
     *
     * <p>Future adapters whose engines return scores outside {@code [0, 1]} (e.g.
     * raw BM25, dot-product similarity, or RRF reciprocal sums) must implement
     * real normalization — min-max scaling, a monotonic transform, or equivalent —
     * rather than re-using this method. See {@link com.mudassir.eka.domain.retrieval.port.RetrievalPort}
     * for the score contract all adapters must satisfy.
     */
    double clampToUnitRange(double rawScore) {
        return Math.max(0.0, Math.min(1.0, rawScore));
    }
}
