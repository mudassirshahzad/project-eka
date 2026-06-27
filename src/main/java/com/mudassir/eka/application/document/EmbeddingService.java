package com.mudassir.eka.application.document;

import com.mudassir.eka.domain.chunk.Chunk;
import com.mudassir.eka.domain.chunk.EmbeddingProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class EmbeddingService {

    private static final long INITIAL_BACKOFF_MS = 100L;
    private static final long MAX_BACKOFF_MS     = 1000L;
    private static final int  MAX_RETRIES        = 3;

    private final EmbeddingProvider embeddingProvider;
    private final int               batchSize;

    public EmbeddingService(
            EmbeddingProvider embeddingProvider,
            @Value("${app.ingestion.embedding-batch-size:32}") int batchSize
    ) {
        this.embeddingProvider = embeddingProvider;
        this.batchSize         = batchSize;
    }

    public List<EmbeddedChunk> embed(List<Chunk> chunks) {
        if (chunks.isEmpty()) return List.of();
        List<EmbeddedChunk> results = new ArrayList<>(chunks.size());
        int batchStart = 0;
        while (batchStart < chunks.size()) {
            int batchEnd    = Math.min(batchStart + batchSize, chunks.size());
            List<Chunk>   batch   = chunks.subList(batchStart, batchEnd);
            List<String>  texts   = batch.stream().map(Chunk::getContent).toList();
            List<float[]> vectors = embedWithRetry(texts);
            Instant embeddedAt    = Instant.now();
            for (int i = 0; i < batch.size(); i++) {
                Chunk chunk = batch.get(i);
                chunk.assignEmbeddingProvenance(
                        embeddingProvider.modelName(),
                        embeddingProvider.dimension(),
                        embeddedAt
                );
                results.add(new EmbeddedChunk(chunk, vectors.get(i)));
            }
            batchStart = batchEnd;
        }
        return List.copyOf(results);
    }

    private List<float[]> embedWithRetry(List<String> texts) {
        long      backoff       = INITIAL_BACKOFF_MS;
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return embeddingProvider.embed(texts);
            } catch (Exception ex) {
                lastException = ex;
                if (attempt < MAX_RETRIES) {
                    log.warn("Embedding attempt {}/{} failed, retrying in {}ms: {}",
                            attempt, MAX_RETRIES, backoff, ex.getMessage());
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during embedding retry", ie);
                    }
                    backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
                }
            }
        }
        throw new RuntimeException("Embedding failed after " + MAX_RETRIES + " attempts", lastException);
    }
}
