package com.mudassirshahzad.eka.infrastructure.query.rewrite;

import com.mudassirshahzad.eka.domain.retrieval.port.QueryRewritePort;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * {@link QueryRewritePort} implementation backed by the Ollama chat model.
 *
 * <h3>Strategy</h3>
 * <p>Uses a constrained system prompt that instructs the model to return <em>only</em> a
 * rewritten retrieval query — no answers, no explanations, no markdown. The model is
 * configured with temperature 0.1 (via {@code application.yml}) to minimise variation and
 * reduce hallucination risk. Rewriting expands abbreviations, normalises conversational
 * phrasing, and removes ambiguity — all without changing the user's intent.
 *
 * <h3>Failure handling</h3>
 * <p>Any failure (LLM timeout, connection error, blank response) is logged as a warning
 * and the original query is returned unchanged. The retrieval pipeline is never interrupted
 * by a rewrite failure. This is the correct enterprise behaviour: rewriting is a quality
 * improvement, not a requirement.
 *
 * <h3>Disable flag</h3>
 * <p>Set {@code app.retrieval.query-rewrite.enabled=false} to bypass the LLM call entirely.
 * Useful in CI environments without Ollama, or for A/B quality comparisons.
 *
 * <h3>Logging</h3>
 * <p>Logs rewrite duration and skip/fallback events. Query text is never logged to avoid
 * sensitive content appearing in application logs.
 */
@Slf4j
@Component
public class OllamaQueryRewriteAdapter implements QueryRewritePort {

    /**
     * System prompt for the rewrite LLM call.
     *
     * <p>Designed to:
     * <ul>
     *   <li>Prevent the model from answering the user's question</li>
     *   <li>Return only the rewritten query text — no preamble, no markdown</li>
     *   <li>Expand abbreviations that improve retrieval (e.g. "SLA" → "service level agreement")</li>
     *   <li>Normalize conversational phrasing to declarative retrieval terms</li>
     *   <li>Preserve the user's intent exactly</li>
     *   <li>Keep the rewritten query concise</li>
     * </ul>
     */
    static final String SYSTEM_PROMPT = """
            You are a retrieval query optimizer for a document search system.
            Your only task is to rewrite the user's query to improve document retrieval quality.

            Rules:
            - Return ONLY the rewritten query text. Nothing else.
            - Do not answer the question. Do not explain the rewrite. Do not add markdown.
            - Preserve the user's intent exactly. Never change what the user is asking about.
            - Expand abbreviations when doing so improves retrieval (e.g. "SLA" to "service level agreement").
            - Normalize conversational phrasing into clear, declarative retrieval terms.
            - Remove ambiguity where possible without adding assumptions.
            - If the query is already well-formed for retrieval, return it unchanged.
            - Keep the rewritten query concise — similar length to the original.
            """;

    private final ChatModel chatModel;
    private final boolean   enabled;

    public OllamaQueryRewriteAdapter(
            ChatModel chatModel,
            @Value("${app.retrieval.query-rewrite.enabled:true}") boolean enabled) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel must not be null");
        this.enabled   = enabled;
    }

    @Override
    public String rewrite(String queryText, TenantId tenantId) {
        if (!enabled) {
            log.debug("Query rewrite disabled; using original query: tenant={}", tenantId);
            return queryText;
        }

        if (queryText == null || queryText.isBlank()) {
            return queryText;
        }

        long startNano = System.nanoTime();
        try {
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(SYSTEM_PROMPT),
                    new UserMessage(queryText)));

            ChatResponse response = chatModel.call(prompt);
            String rewritten = response.getResult().getOutput().getText().strip();

            if (rewritten.isBlank()) {
                log.debug("Query rewrite returned blank; falling back to original: tenant={} latencyMs={}",
                        tenantId, elapsedMs(startNano));
                return queryText;
            }

            log.debug("Query rewrite completed: tenant={} latencyMs={}", tenantId, elapsedMs(startNano));
            return rewritten;

        } catch (Exception ex) {
            log.warn("Query rewrite failed, falling back to original query: tenant={} reason={}",
                    tenantId, ex.getMessage());
            return queryText;
        }
    }

    private static long elapsedMs(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000L;
    }
}
