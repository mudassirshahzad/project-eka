package com.mudassir.eka.infrastructure.context;

import com.mudassir.eka.domain.chunk.ChunkId;
import com.mudassir.eka.domain.retrieval.model.AssembledChunk;
import com.mudassir.eka.domain.retrieval.model.AssembledContext;
import com.mudassir.eka.domain.retrieval.model.RetrievedChunk;
import com.mudassir.eka.domain.retrieval.port.ContextAssemblyPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Default implementation of {@link ContextAssemblyPort}.
 *
 * <p>Assembly rules (applied in order):
 * <ol>
 *   <li>Iterate ranked chunks in input order (RRF order = descending relevance)</li>
 *   <li>Skip any chunk whose {@link ChunkId} has already been seen (deduplication)</li>
 *   <li>Stop when the next chunk would cause cumulative estimated tokens to exceed
 *       {@code tokenBudget} (strict budget: the overflowing chunk is excluded)</li>
 *   <li>Stop when the number of included chunks would exceed {@code maxChunks}</li>
 * </ol>
 *
 * <p>Token estimation uses the 4-chars-per-token heuristic.  Precise tokenization requires
 * an external tokenizer dependency and is deferred to a future milestone.
 *
 * <p>This adapter never calls an LLM, never generates prose, and never modifies chunk content.
 */
@Slf4j
@Component
public class DefaultContextAssemblyAdapter implements ContextAssemblyPort {

    static final int CHARS_PER_TOKEN = 4;

    private final int maxChunks;

    public DefaultContextAssemblyAdapter(
            @Value("${app.context.max-chunks:10}") int maxChunks) {
        if (maxChunks < 1) {
            throw new IllegalArgumentException("maxChunks must be >= 1 but was " + maxChunks);
        }
        this.maxChunks = maxChunks;
    }

    @Override
    public AssembledContext assemble(List<RetrievedChunk> chunks, String queryText, int tokenBudget) {
        Objects.requireNonNull(chunks,    "chunks must not be null");
        Objects.requireNonNull(queryText, "queryText must not be null");
        if (tokenBudget < 0) {
            throw new IllegalArgumentException("tokenBudget must be >= 0 but was " + tokenBudget);
        }

        if (chunks.isEmpty() || tokenBudget == 0) {
            return new AssembledContext(List.of(), queryText, tokenBudget, 0);
        }

        Set<ChunkId>        seen              = new HashSet<>();
        List<AssembledChunk> assembled        = new ArrayList<>();
        int                  accumulatedTokens = 0;
        int                  position          = 0;

        for (RetrievedChunk chunk : chunks) {
            if (!seen.add(chunk.chunkId())) {
                continue;
            }

            if (position >= maxChunks) {
                break;
            }

            int chunkTokens = estimateTokens(chunk.content());
            if (accumulatedTokens + chunkTokens > tokenBudget) {
                break;
            }

            assembled.add(new AssembledChunk(
                    chunk.chunkId(),
                    chunk.documentId(),
                    chunk.tenantId(),
                    chunk.content(),
                    chunk.score(),
                    position));

            accumulatedTokens += chunkTokens;
            position++;
        }

        log.debug("Context assembled: chunks={} estimatedTokens={} tokenBudget={} maxChunks={}",
                assembled.size(), accumulatedTokens, tokenBudget, maxChunks);

        return new AssembledContext(assembled, queryText, tokenBudget, accumulatedTokens);
    }

    static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / CHARS_PER_TOKEN);
    }
}
