package com.mudassirshahzad.eka.domain.retrieval.model;

import java.util.List;
import java.util.Objects;

/**
 * @param effectiveQueryText the query text actually used for this retrieval — after
 *                            {@code QueryRewritePort} rewriting, if any (ADR O04). Callers that
 *                            assemble context from this result (e.g. {@code ContextAssemblyPort})
 *                            must use this value, not the caller's original query text, so
 *                            {@link AssembledContext#queryText()}'s documented contract
 *                            ("mirrors what retrieval used") actually holds.
 */
public record RetrievalResult(List<RetrievedChunk> items, SearchMetadata metadata, String effectiveQueryText) {

    public RetrievalResult {
        Objects.requireNonNull(items,             "items must not be null");
        Objects.requireNonNull(metadata,          "metadata must not be null");
        Objects.requireNonNull(effectiveQueryText, "effectiveQueryText must not be null");
        items = List.copyOf(items);
    }

    public static RetrievalResult empty(String strategy, long latencyMs, String effectiveQueryText) {
        return new RetrievalResult(List.of(), new SearchMetadata(0, latencyMs, strategy), effectiveQueryText);
    }

    public boolean hasResults() {
        return !items.isEmpty();
    }

    public int size() {
        return items.size();
    }
}
