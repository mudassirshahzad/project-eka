package com.mudassirshahzad.eka.infrastructure.query.rewrite;

import com.mudassirshahzad.eka.domain.retrieval.port.QueryRewritePort;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * HyDE — Hypothetical Document Embeddings (WP-4, ADR RQ03).
 *
 * <h3>What it does differently from {@link OllamaQueryRewriteAdapter}</h3>
 * <p>Rewriting normalises the <em>question</em> and retrieves with it. HyDE instead asks the model
 * to write a short passage that would <em>answer</em> the question, and retrieves with that. The
 * premise is that a hypothetical answer sits closer in embedding space to the real answer than the
 * question does, because questions and answers are written in different registers — "what is our
 * refund window?" shares few terms with "Refunds are accepted within 30 days of purchase."
 *
 * <h3>Why this is a {@link QueryRewritePort} implementation and not a new port</h3>
 * <p>HyDE occupies exactly the existing seam: transform the query text before retrieval uses it.
 * Introducing a parallel port would duplicate the contract, and — more importantly — would make the
 * two strategies combinable in ways nobody has specified. Being a second implementation of the same
 * port makes them mutually exclusive by construction and switchable by configuration, which is what
 * an A/B quality comparison needs.
 *
 * <h3>Selection and failure behaviour</h3>
 * <p>Off by default; {@code app.retrieval.hyde.enabled=true} makes this bean {@code @Primary} in
 * place of the plain rewriter. Any failure — model unreachable, blank reply — returns the original
 * query unchanged, the same contract {@link OllamaQueryRewriteAdapter} already honours: a retrieval
 * request must never fail because a quality enhancement did.
 *
 * <h3>Cost</h3>
 * <p>One model call before retrieval even starts, on top of re-ranking's call per candidate. That
 * cumulative latency is precisely why both are opt-in and why the evaluation harness exists — the
 * question "does this actually retrieve better?" should be answered with a measurement, not an
 * assumption.
 */
@Slf4j
@Component
@Primary
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "app.retrieval.hyde.enabled", havingValue = "true")
public class HydeQueryRewriteAdapter implements QueryRewritePort {

    static final String SYSTEM_PROMPT = """
            You write a short, factual passage that would answer the user's question,
            as if it were an excerpt from an internal company document.
            Write 2-3 sentences. Use plain declarative statements.
            Do not say you are unsure. Do not ask questions.
            Do not add a preamble, heading, or markdown.
            Invented specifics are acceptable: this passage is used only to improve
            document search, and is never shown to a user.
            """;

    private final ChatModel chatModel;
    private final boolean   enabled;

    public HydeQueryRewriteAdapter(
            ChatModel chatModel,
            @Value("${app.retrieval.hyde.enabled:false}") boolean enabled) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel must not be null");
        this.enabled   = enabled;
    }

    @Override
    public String rewrite(String queryText, TenantId tenantId) {
        if (!enabled || queryText == null || queryText.isBlank()) {
            return queryText;
        }

        long startNano = System.nanoTime();
        try {
            String hypothetical = chatModel
                    .call(new Prompt(List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(queryText))))
                    .getResult().getOutput().getText();

            if (hypothetical == null || hypothetical.isBlank()) {
                log.warn("HyDE returned blank output; falling back to the original query");
                return queryText;
            }

            // Retrieval matches against the hypothetical answer, but the original question is
            // appended so a strong lexical match on the user's own terms is not lost — BM25 scores
            // the query terms, not the invented ones.
            String effective = hypothetical.strip() + "\n\n" + queryText;

            log.debug("HyDE expansion complete: tenant={} latencyMs={}",
                    tenantId, (System.nanoTime() - startNano) / 1_000_000L);
            return effective;
        } catch (RuntimeException ex) {
            // Query text is never logged (Security/Logging Policy).
            log.warn("HyDE expansion failed ({}); falling back to the original query",
                    ex.getClass().getSimpleName());
            return queryText;
        }
    }
}
