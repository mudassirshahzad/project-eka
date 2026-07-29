package com.mudassirshahzad.eka.domain.retrieval.model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable output of the Context Assembly layer, ready for consumption by the Prompt Builder.
 *
 * <p>Carries the full ordered, deduplicated list of {@link AssembledChunk}s alongside the
 * effective query text that was used to retrieve them, the token budget that was applied, and
 * the estimated token usage of the included chunks.  The Prompt Builder must consume this object
 * directly and must not access retrieval internals; all citation identity needed for future
 * Citation Generation is preserved inside each {@link AssembledChunk}.
 *
 * <p>The {@code chunks} list is guaranteed to be:
 * <ul>
 *   <li>Ordered by descending relevance (position 0 = most relevant)</li>
 *   <li>Deduplicated by {@link com.mudassirshahzad.eka.domain.chunk.ChunkId}</li>
 *   <li>Non-empty only when at least one chunk fits within {@code tokenBudget}</li>
 *   <li>Unmodifiable — structural mutation is not supported</li>
 * </ul>
 *
 * @param chunks          ordered assembled chunks (may be empty if none fit the budget)
 * @param queryText       the effective query text after rewriting (mirrors what retrieval used)
 * @param tokenBudget     the budget that was enforced during assembly
 * @param estimatedTokens actual estimated token count of all included chunks (≤ tokenBudget)
 */
public record AssembledContext(
        List<AssembledChunk> chunks,
        String               queryText,
        int                  tokenBudget,
        int                  estimatedTokens) {

    public AssembledContext {
        Objects.requireNonNull(chunks,    "chunks must not be null");
        Objects.requireNonNull(queryText, "queryText must not be null");
        if (tokenBudget     < 0) throw new IllegalArgumentException("tokenBudget must be >= 0 but was "     + tokenBudget);
        if (estimatedTokens < 0) throw new IllegalArgumentException("estimatedTokens must be >= 0 but was " + estimatedTokens);
        chunks = List.copyOf(chunks);
    }

    public boolean isEmpty() {
        return chunks.isEmpty();
    }

    public int size() {
        return chunks.size();
    }
}
