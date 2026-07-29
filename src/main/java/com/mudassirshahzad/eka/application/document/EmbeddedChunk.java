package com.mudassirshahzad.eka.application.document;

import com.mudassirshahzad.eka.domain.chunk.Chunk;

public record EmbeddedChunk(Chunk chunk, float[] embedding) {}
