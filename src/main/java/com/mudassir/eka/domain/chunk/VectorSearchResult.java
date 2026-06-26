package com.mudassir.eka.domain.chunk;

public record VectorSearchResult(
        ChunkId chunkId,
        String  vectorId,
        String  content,
        double  score
) {}
