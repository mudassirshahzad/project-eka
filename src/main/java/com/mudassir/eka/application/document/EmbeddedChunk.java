package com.mudassir.eka.application.document;

import com.mudassir.eka.domain.chunk.Chunk;

public record EmbeddedChunk(Chunk chunk, float[] embedding) {}
