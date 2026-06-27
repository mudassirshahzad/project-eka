package com.mudassir.eka.domain.retrieval.model;

import java.util.List;
import java.util.Objects;

public record RetrievalResult(List<RetrievedChunk> items, SearchMetadata metadata) {

    public RetrievalResult {
        Objects.requireNonNull(items,    "items must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        items = List.copyOf(items);
    }

    public static RetrievalResult empty(String strategy, long latencyMs) {
        return new RetrievalResult(List.of(), new SearchMetadata(0, latencyMs, strategy));
    }

    public boolean hasResults() {
        return !items.isEmpty();
    }

    public int size() {
        return items.size();
    }
}
