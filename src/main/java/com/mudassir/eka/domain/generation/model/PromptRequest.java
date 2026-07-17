package com.mudassir.eka.domain.generation.model;

import com.mudassir.eka.domain.conversation.Message;

import java.util.List;
import java.util.Objects;

/**
 * Provider-independent prompt specification produced by the Prompt Builder (ADR G01).
 *
 * <p>This is the contract between {@link com.mudassir.eka.domain.generation.port.PromptBuilderPort}
 * and the future {@code LlmPort}.  It carries everything the LLM adapter needs to construct a
 * provider-specific prompt without any Spring AI, Ollama, or OpenAI types appearing here.
 *
 * <p>The LLM adapter (introduced in P04.9) will convert this record into a
 * {@code org.springframework.ai.chat.prompt.Prompt} inside the infrastructure layer — that
 * conversion is the only place where Spring AI types may appear.
 *
 * <ul>
 *   <li>{@code systemText} — fully rendered system instructions, including the assembled context
 *       with SOURCE markers already injected</li>
 *   <li>{@code userText} — the user's original verbatim question (ADR G02)</li>
 *   <li>{@code memoryMessages} — conversation history in chronological order; empty today</li>
 *   <li>{@code tools} — tool definitions passed through unchanged; empty today (agent milestone)</li>
 * </ul>
 *
 * @param systemText     rendered system prompt containing context and instructions; must not be null
 * @param userText       the user's original verbatim question; must not be null
 * @param memoryMessages recent conversation turns; empty today, populated in P04.10
 * @param tools          callable tool definitions; empty today, populated at the agent milestone
 */
public record PromptRequest(
        String               systemText,
        String               userText,
        List<Message>        memoryMessages,
        List<ToolDefinition> tools) {

    public PromptRequest {
        Objects.requireNonNull(systemText, "systemText must not be null");
        Objects.requireNonNull(userText,   "userText must not be null");
        memoryMessages = memoryMessages != null ? List.copyOf(memoryMessages) : List.of();
        tools          = tools          != null ? List.copyOf(tools)          : List.of();
    }
}
