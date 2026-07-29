package com.mudassirshahzad.eka.domain.generation.model;

import com.mudassirshahzad.eka.domain.conversation.Message;
import com.mudassirshahzad.eka.domain.retrieval.model.AssembledContext;
import com.mudassirshahzad.eka.domain.shared.TenantId;

import java.util.List;
import java.util.Objects;

/**
 * Single immutable input to {@link com.mudassirshahzad.eka.domain.generation.port.PromptBuilderPort}.
 *
 * <p>This is the frozen public API for the Prompt Builder (ADR G08).  All current and future
 * inputs travel through this record so the port signature never changes:
 * <ul>
 *   <li>{@code assembledContext} — ranked, deduplicated chunks from Context Assembly</li>
 *   <li>{@code originalQueryText} — the user's verbatim question (ADR G02: never the rewritten form)</li>
 *   <li>{@code memoryMessages} — conversation history; {@code List.of()} today, populated in P04.10</li>
 *   <li>{@code tools} — available tool definitions; {@code List.of()} today, populated at the agent milestone (ADR G07)</li>
 *   <li>{@code tenantId} — owner tenant for audit and future authorisation checks</li>
 * </ul>
 *
 * <p>Null values for {@code memoryMessages} and {@code tools} are accepted and normalised to an
 * empty list, so callers that omit them today are forward-compatible with P04.10 and beyond.
 *
 * @param assembledContext  ordered, deduplicated context ready for prompt injection; must not be null
 * @param originalQueryText the user's original question verbatim; must not be null
 * @param memoryMessages    recent conversation turns for memory-augmented generation; empty today
 * @param tools             callable tools available to the model; empty today (agent milestone)
 * @param tenantId          owning tenant; must not be null
 */
public record PromptBuildRequest(
        AssembledContext      assembledContext,
        String               originalQueryText,
        List<Message>        memoryMessages,
        List<ToolDefinition> tools,
        TenantId             tenantId) {

    public PromptBuildRequest {
        Objects.requireNonNull(assembledContext,   "assembledContext must not be null");
        Objects.requireNonNull(originalQueryText,  "originalQueryText must not be null");
        Objects.requireNonNull(tenantId,           "tenantId must not be null");
        memoryMessages = memoryMessages != null ? List.copyOf(memoryMessages) : List.of();
        tools          = tools          != null ? List.copyOf(tools)          : List.of();
    }
}
