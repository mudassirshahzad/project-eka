package com.mudassir.eka.domain.retrieval.port;

import com.mudassir.eka.domain.retrieval.model.AssembledContext;
import com.mudassir.eka.domain.retrieval.model.RetrievedChunk;

import java.util.List;

/**
 * Port for assembling retrieved chunks into a clean, immutable context ready for Prompt Builder.
 *
 * <p>Implementations must:
 * <ul>
 *   <li>Preserve the ranking order established by the upstream retrieval and ranking layers</li>
 *   <li>Deduplicate chunks by {@link com.mudassir.eka.domain.chunk.ChunkId}</li>
 *   <li>Enforce the caller-supplied {@code tokenBudget} by stopping at the first chunk that
 *       would cause the cumulative estimated token count to exceed it</li>
 *   <li>Never call an LLM, never generate prose, never summarize chunks</li>
 *   <li>Return an {@link AssembledContext} carrying full citation metadata for downstream
 *       Citation Generation</li>
 * </ul>
 */
public interface ContextAssemblyPort {

    AssembledContext assemble(List<RetrievedChunk> chunks, String queryText, int tokenBudget);
}
