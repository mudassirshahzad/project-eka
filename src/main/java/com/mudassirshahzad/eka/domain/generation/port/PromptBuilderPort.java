package com.mudassirshahzad.eka.domain.generation.port;

import com.mudassirshahzad.eka.domain.generation.model.PromptBuildRequest;
import com.mudassirshahzad.eka.domain.generation.model.PromptRequest;

/**
 * Port for constructing a provider-independent prompt from structured domain inputs (ADR G01, G08).
 *
 * <p>Implementations must:
 * <ul>
 *   <li>Produce a {@link PromptRequest} containing no Spring AI, Ollama, or OpenAI types</li>
 *   <li>Use the {@link PromptBuildRequest#originalQueryText()} as the user turn (ADR G02 — never the rewritten form)</li>
 *   <li>Inject SOURCE markers from {@link com.mudassirshahzad.eka.domain.retrieval.model.AssembledChunk#position()} (ADR G03)</li>
 *   <li>Preserve retrieval ordering exactly as received from Context Assembly</li>
 *   <li>Never call an LLM, never rewrite queries, never merge or summarize chunks</li>
 *   <li>Remain stateless — the same inputs always produce the same output (ADR G04)</li>
 * </ul>
 *
 * <p>The port signature is frozen.  All current and future inputs arrive via the single
 * {@link PromptBuildRequest} record; adding new inputs in future milestones (memory, tools,
 * persona) requires only a new field on the record, not a change to this interface.
 */
public interface PromptBuilderPort {

    PromptRequest build(PromptBuildRequest request);
}
