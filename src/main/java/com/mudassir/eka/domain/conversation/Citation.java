package com.mudassir.eka.domain.conversation;

import com.mudassir.eka.domain.chunk.ChunkId;

public record Citation(
        ChunkId chunkId,
        double  relevanceScore
) {

    public Citation {
        if (chunkId == null) throw new IllegalArgumentException("chunkId must not be null");
        if (relevanceScore < 0.0 || relevanceScore > 1.0) {
            throw new IllegalArgumentException("relevanceScore must be in [0.0, 1.0]");
        }
    }

    public static Citation of(ChunkId chunkId, double relevanceScore) {
        return new Citation(chunkId, relevanceScore);
    }
}
