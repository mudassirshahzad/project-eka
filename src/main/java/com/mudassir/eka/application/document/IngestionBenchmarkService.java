package com.mudassir.eka.application.document;

import com.mudassir.eka.domain.chunk.Chunk;
import com.mudassir.eka.domain.chunk.EmbeddingProvider;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.document.ParsedDocument;
import com.mudassir.eka.domain.shared.TenantId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionBenchmarkService {

    private final ChunkingService         chunkingService;
    private final EmbeddingService        embeddingService;
    private final ChunkApplicationService chunkApplicationService;
    private final EmbeddingProvider       embeddingProvider;

    public BenchmarkReport benchmark(ParsedDocument parsedDocument,
                                     DocumentId documentId,
                                     TenantId tenantId) {
        Instant totalStart = Instant.now();

        Instant chunkStart    = Instant.now();
        List<Chunk> chunks    = chunkingService.chunk(parsedDocument, documentId, tenantId);
        Duration    chunkTime = Duration.between(chunkStart, Instant.now());

        Instant embedStart      = Instant.now();
        List<EmbeddedChunk> embedded = embeddingService.embed(chunks);
        Duration    embedTime   = Duration.between(embedStart, Instant.now());

        Instant persistStart  = Instant.now();
        List<Chunk> saved     = chunkApplicationService.saveAll(embedded);
        Duration persistTime  = Duration.between(persistStart, Instant.now());

        Duration totalTime = Duration.between(totalStart, Instant.now());

        BenchmarkReport report = new BenchmarkReport(
                saved.size(),
                Duration.ZERO,
                chunkTime,
                embedTime,
                persistTime,
                totalTime,
                embeddingProvider.modelName(),
                embeddingProvider.dimension()
        );
        log.info(report.summary());
        return report;
    }
}
