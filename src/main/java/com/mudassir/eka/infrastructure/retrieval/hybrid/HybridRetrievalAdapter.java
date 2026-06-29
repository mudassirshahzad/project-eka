package com.mudassir.eka.infrastructure.retrieval.hybrid;

import com.mudassir.eka.domain.query.MetadataFilter;
import com.mudassir.eka.domain.retrieval.model.RetrievalOptions;
import com.mudassir.eka.domain.retrieval.model.RetrievalResult;
import com.mudassir.eka.domain.retrieval.model.RetrievedChunk;
import com.mudassir.eka.domain.retrieval.model.SearchMetadata;
import com.mudassir.eka.domain.retrieval.port.RetrievalPort;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.infrastructure.retrieval.hybrid.exception.HybridRetrievalException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Composite {@link RetrievalPort} that orchestrates vector and BM25 retrieval engines,
 * concatenating their results into a single list for downstream RRF ranking.
 *
 * <h3>Execution model</h3>
 * <p>Retrieval engines are called <em>sequentially</em>. Parallel execution would reduce
 * latency by approximately one engine's response time, but adds substantial complexity
 * ({@code CompletableFuture} management, executor configuration, parallel exception
 * semantics). Sequential execution is preferred until measured latency data justifies
 * the trade-off.
 *
 * <h3>Failure handling</h3>
 * <p>Individual engine failures are tolerated: a warning is logged and the surviving
 * engine's results are used. Only when <em>both</em> engines fail is a
 * {@link HybridRetrievalException} thrown. The {@link SearchMetadata#strategy()} field
 * in the result reflects the degraded mode ({@code "hybrid:vector-only"} or
 * {@code "hybrid:bm25-only"}) so that downstream consumers and monitoring can detect it.
 *
 * <h3>Duplicate handling</h3>
 * <p>Chunks found by both engines appear twice in the concatenated result list. Duplicate
 * resolution is the responsibility of the {@link com.mudassir.eka.domain.retrieval.port.RankingPort}
 * (RRF), which accumulates contributions from every occurrence and emits one merged entry per
 * unique {@link com.mudassir.eka.domain.chunk.ChunkId}. This adapter does not deduplicate.
 *
 * <h3>SearchMetadata</h3>
 * <p>{@code totalHits} reflects the combined pre-deduplication count from both engines.
 * {@code latencyMs} covers the full sequential retrieval wall-clock time.
 * {@code strategy} is {@code "hybrid"} (or a degraded variant) — the RRF ranking step
 * that follows in {@link com.mudassir.eka.application.retrieval.RetrievalService}
 * preserves this metadata unchanged in the final result.
 */
@Slf4j
@Primary
@Component
public class HybridRetrievalAdapter implements RetrievalPort {

    private static final String STRATEGY_HYBRID       = "hybrid";
    private static final String STRATEGY_VECTOR_ONLY  = "hybrid:vector-only";
    private static final String STRATEGY_BM25_ONLY    = "hybrid:bm25-only";

    private final RetrievalPort vectorRetrievalPort;
    private final RetrievalPort bm25RetrievalPort;

    public HybridRetrievalAdapter(
            @Qualifier("vectorRetrieval") RetrievalPort vectorRetrievalPort,
            @Qualifier("bm25Retrieval")   RetrievalPort bm25RetrievalPort) {
        this.vectorRetrievalPort = vectorRetrievalPort;
        this.bm25RetrievalPort   = bm25RetrievalPort;
    }

    @Override
    public RetrievalResult retrieve(
            String queryText,
            TenantId tenantId,
            MetadataFilter filter,
            RetrievalOptions options) {

        long startNano = System.nanoTime();

        // ── Vector retrieval ─────────────────────────────────────────────────
        List<RetrievedChunk> vectorChunks;
        boolean vectorSucceeded;
        try {
            RetrievalResult vectorResult = vectorRetrievalPort.retrieve(queryText, tenantId, filter, options);
            vectorChunks    = vectorResult.items();
            vectorSucceeded = true;
            log.debug("Hybrid[vector]: tenant={} hits={}", tenantId, vectorChunks.size());
        } catch (Exception ex) {
            log.warn("Hybrid[vector] failed, continuing with BM25 only: tenant={} reason={}",
                    tenantId, ex.getMessage());
            vectorChunks    = List.of();
            vectorSucceeded = false;
        }

        // ── BM25 retrieval ───────────────────────────────────────────────────
        List<RetrievedChunk> bm25Chunks;
        boolean bm25Succeeded;
        try {
            RetrievalResult bm25Result = bm25RetrievalPort.retrieve(queryText, tenantId, filter, options);
            bm25Chunks    = bm25Result.items();
            bm25Succeeded = true;
            log.debug("Hybrid[bm25]: tenant={} hits={}", tenantId, bm25Chunks.size());
        } catch (Exception ex) {
            log.warn("Hybrid[bm25] failed, continuing with vector only: tenant={} reason={}",
                    tenantId, ex.getMessage());
            bm25Chunks    = List.of();
            bm25Succeeded = false;
        }

        if (!vectorSucceeded && !bm25Succeeded) {
            throw new HybridRetrievalException(
                    "Both retrieval engines failed for tenant=" + tenantId
                    + ". At least one engine must succeed.");
        }

        // ── Concatenate — duplicates resolved by downstream RRF ──────────────
        List<RetrievedChunk> combined = new ArrayList<>(vectorChunks.size() + bm25Chunks.size());
        combined.addAll(vectorChunks);
        combined.addAll(bm25Chunks);

        long latencyMs = (System.nanoTime() - startNano) / 1_000_000L;
        String strategy = effectiveStrategy(vectorSucceeded, bm25Succeeded);

        log.info("Hybrid retrieval: tenant={} vectorHits={} bm25Hits={} totalHits={} latencyMs={} strategy={}",
                tenantId, vectorChunks.size(), bm25Chunks.size(), combined.size(), latencyMs, strategy);

        return new RetrievalResult(combined, new SearchMetadata(combined.size(), latencyMs, strategy));
    }

    private static String effectiveStrategy(boolean vectorOk, boolean bm25Ok) {
        if (vectorOk && bm25Ok) return STRATEGY_HYBRID;
        if (vectorOk)           return STRATEGY_VECTOR_ONLY;
        return                         STRATEGY_BM25_ONLY;
    }
}
