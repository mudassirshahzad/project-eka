package com.mudassir.eka.domain.chunk;

import java.util.UUID;

public record ChunkId(UUID value) {

    public ChunkId {
        if (value == null) throw new IllegalArgumentException("ChunkId value must not be null");
    }

    public static ChunkId generate() {
        return new ChunkId(UUID.randomUUID());
    }

    public static ChunkId of(UUID value) {
        return new ChunkId(value);
    }

    public static ChunkId of(String value) {
        return new ChunkId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
