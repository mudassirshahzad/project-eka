package com.mudassirshahzad.eka.infrastructure.prompt;

import com.mudassirshahzad.eka.domain.generation.model.PromptBuildRequest;
import com.mudassirshahzad.eka.domain.generation.model.PromptRequest;
import com.mudassirshahzad.eka.domain.generation.port.PromptBuilderPort;
import com.mudassirshahzad.eka.domain.retrieval.model.AssembledChunk;
import com.mudassirshahzad.eka.domain.retrieval.model.AssembledContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * {@link PromptBuilderPort} implementation that builds prompts from an external template.
 *
 * <h3>Template loading</h3>
 * <p>The system prompt template is loaded from the classpath at application startup via
 * {@code classpath:prompts/qa-system.txt}.  If the file is absent or unreadable the application
 * fails to start with a clear {@link IllegalStateException} — there is no fallback and no
 * silent degradation (ADR G06: fail fast).
 *
 * <h3>Context rendering</h3>
 * <p>Each {@link AssembledChunk} in the {@link AssembledContext} is rendered as:
 * <pre>
 * [SOURCE:N]
 * {chunk content}
 * </pre>
 * where {@code N = chunk.position() + 1} (1-based per ADR G03).  Chunks are separated by a
 * blank line.  When the context is empty a short fallback message replaces the context block so
 * the LLM still receives a coherent system prompt.
 *
 * <h3>Provider independence</h3>
 * <p>This adapter uses Spring's {@link Resource} abstraction for template loading only.  The
 * {@link PromptRequest} it produces contains no Spring AI, Ollama, or OpenAI types; those
 * conversions happen exclusively in the LLM adapter introduced in P04.9 (ADR G01).
 *
 * <h3>Statelessness</h3>
 * <p>The template string is loaded once and is effectively final.  All state needed for a call
 * arrives in the {@link PromptBuildRequest}; the same inputs always produce the same output
 * (ADR G04).
 *
 * <h3>Logging</h3>
 * <p>Debug-level only.  The system text and user query are never logged to avoid sensitive
 * document content appearing in application logs.
 */
@Slf4j
@Component
public class TemplateBasedPromptBuilderAdapter implements PromptBuilderPort {

    static final String CONTEXT_PLACEHOLDER     = "{context}";
    static final String EMPTY_CONTEXT_FALLBACK  =
            "No relevant context was found for this query.";

    private final String systemTemplate;

    public TemplateBasedPromptBuilderAdapter(
            @Value("classpath:prompts/qa-system.txt") Resource templateResource) {
        this.systemTemplate = loadTemplate(templateResource);
        log.debug("Prompt template loaded: description={} length={}",
                templateResource.getDescription(), systemTemplate.length());
    }

    @Override
    public PromptRequest build(PromptBuildRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        String contextBlock = renderContext(request.assembledContext());
        String systemText   = systemTemplate.replace(CONTEXT_PLACEHOLDER, contextBlock);

        log.debug("Prompt built: chunks={} queryLength={} memoryMessages={} tools={}",
                request.assembledContext().size(),
                request.originalQueryText().length(),
                request.memoryMessages().size(),
                request.tools().size());

        return new PromptRequest(
                systemText,
                request.originalQueryText(),
                request.memoryMessages(),
                request.tools());
    }

    static String renderContext(AssembledContext context) {
        if (context.isEmpty()) {
            return EMPTY_CONTEXT_FALLBACK;
        }
        var sb = new StringBuilder();
        for (AssembledChunk chunk : context.chunks()) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append("[SOURCE:").append(chunk.position() + 1).append("]\n");
            sb.append(chunk.content());
        }
        return sb.toString();
    }

    private static String loadTemplate(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Required prompt template not found: " + resource.getDescription(), e);
        }
    }
}
