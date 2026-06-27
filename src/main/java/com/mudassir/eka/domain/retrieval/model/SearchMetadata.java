package com.mudassir.eka.domain.retrieval.model;

public record SearchMetadata(int totalHits, long latencyMs, String strategy) {

    public SearchMetadata {
        if (totalHits < 0) {
            throw new IllegalArgumentException("totalHits must be >= 0 but was " + totalHits);
        }
        if (latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs must be >= 0 but was " + latencyMs);
        }
        if (strategy == null || strategy.isBlank()) {
            throw new IllegalArgumentException("strategy must not be blank");
        }
    }
}
