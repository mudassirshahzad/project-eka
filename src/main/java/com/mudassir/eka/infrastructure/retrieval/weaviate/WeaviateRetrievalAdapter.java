package com.mudassir.eka.infrastructure.retrieval.weaviate;

import com.mudassir.eka.domain.chunk.Chunk;
import com.mudassir.eka.domain.chunk.ChunkId;
import com.mudassir.eka.domain.chunk.ChunkRepository;
import com.mudassir.eka.domain.chunk.VectorSearchResult;
import com.mudassir.eka.domain.chunk.VectorStore;
import com.mudassir.eka.domain.query.MetadataFilter;
import com.mudassir.eka.domain.retrieval.model.RetrievalOptions;
import com.mudassir.eka.domain.retrieval.model.RetrievalResult;
import com.mudassir.eka.domain.retrieval.model.RetrievedChunk;
import com.mudassir.eka.domain.retrieval.model.SearchMetadata;
import com.mudassir.eka.domain.retrieval.port.RetrievalPort;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.infrastructure.retrieval.weaviate.exception.RetrievalAdapterException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Qualifier("vectorRetrieval")
@Component
@RequiredArgsConstructor
public class WeaviateRetrievalAdapter implements RetrievalPort {

    private static final String STRATEGY = "vector";

    private final VectorStore          vectorStore;
    private final ChunkRepository      chunkRepository;
    private final RetrievedChunkMapper mapper;

    @Override
    public RetrievalResult retrieve(
            String queryText,
            TenantId tenantId,
            MetadataFilter filter,
            RetrievalOptions options) {

        long startNano = System.nanoTime();

        MetadataFilter tenantScopedFilter = withTenantIsolation(tenantId, filter);

        List<VectorSearchResult> rawResults;
        try {
            rawResults = vectorStore.search(queryText, options.topK(), tenantScopedFilter);
        } catch (Exception ex) {
            throw new RetrievalAdapterException(
                    "Vector retrieval failed for tenant=" + tenantId, ex);
        }

        if (rawResults.isEmpty()) {
            long latencyMs = elapsedMs(startNano);
            log.debug("Retrieval: tenant={} topK={} hits=0 latencyMs={}", tenantId, options.topK(), latencyMs);
            return RetrievalResult.empty(STRATEGY, latencyMs);
        }

        List<ChunkId> chunkIds = rawResults.stream()
                .map(VectorSearchResult::chunkId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        if (chunkIds.isEmpty()) {
            long latencyMs = elapsedMs(startNano);
            log.debug("Retrieval: tenant={} topK={} hits=0 latencyMs={}", tenantId, options.topK(), latencyMs);
            return RetrievalResult.empty(STRATEGY, latencyMs);
        }

        Map<ChunkId, Chunk> chunkById;
        try {
            chunkById = chunkRepository.findByIds(chunkIds).stream()
                    .collect(Collectors.toMap(Chunk::getId, Function.identity()));
        } catch (Exception ex) {
            throw new RetrievalAdapterException(
                    "Failed to load chunk metadata for " + chunkIds.size() + " chunk(s) in tenant=" + tenantId, ex);
        }

        List<RetrievedChunk> chunks = new ArrayList<>();
        for (int rawRank = 0; rawRank < rawResults.size(); rawRank++) {
            VectorSearchResult raw = rawResults.get(rawRank);
            if (raw.chunkId() == null) continue;
            Chunk chunk = chunkById.get(raw.chunkId());
            if (chunk == null) continue;

            RetrievedChunk candidate = mapper.toRetrievedChunk(raw, chunk, rawRank);
            if (candidate.score() >= options.minimumScore()) {
                chunks.add(candidate);
            }
        }

        long latencyMs = elapsedMs(startNano);
        log.debug("Retrieval: tenant={} topK={} hits={} latencyMs={}",
                tenantId, options.topK(), chunks.size(), latencyMs);

        return new RetrievalResult(chunks, new SearchMetadata(chunks.size(), latencyMs, STRATEGY));
    }

    /**
     * Merges the caller's filter with mandatory tenant isolation.
     * The tenantId key is applied last and always wins — cross-tenant retrieval
     * is architecturally impossible regardless of the caller's filter contents.
     */
    private MetadataFilter withTenantIsolation(TenantId tenantId, MetadataFilter userFilter) {
        MetadataFilter.Builder builder = MetadataFilter.builder();
        userFilter.criteria().forEach(builder::put);
        builder.put("tenantId", tenantId.value().toString());
        return builder.build();
    }

    private static long elapsedMs(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000L;
    }
}
