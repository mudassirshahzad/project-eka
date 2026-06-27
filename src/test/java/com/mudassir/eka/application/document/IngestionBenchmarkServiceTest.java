package com.mudassir.eka.application.document;

import com.mudassir.eka.domain.chunk.Chunk;
import com.mudassir.eka.domain.chunk.ChunkMetadata;
import com.mudassir.eka.domain.chunk.EmbeddingProvider;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.document.ParsedDocument;
import com.mudassir.eka.domain.document.ParsedMetadata;
import com.mudassir.eka.domain.document.ParsingStatus;
import com.mudassir.eka.domain.document.SupportedFormat;
import com.mudassir.eka.domain.shared.TenantId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestionBenchmarkServiceTest {

    @Mock private ChunkingService         chunkingService;
    @Mock private EmbeddingService        embeddingService;
    @Mock private ChunkApplicationService chunkApplicationService;
    @Mock private EmbeddingProvider       embeddingProvider;
    @InjectMocks private IngestionBenchmarkService benchmarkService;

    private final DocumentId documentId = DocumentId.generate();
    private final TenantId   tenantId   = TenantId.generate();

    private ParsedDocument parsed(String text) {
        return new ParsedDocument(text,
                new ParsedMetadata(null, null, null, 0, text.length()),
                SupportedFormat.TXT, ParsingStatus.SUCCESS, Instant.now());
    }

    private Chunk makeChunk() {
        return Chunk.create(documentId, tenantId, 0, "content", ChunkMetadata.of("sliding-window"));
    }

    @Test
    void benchmark_returnsReportWithCorrectChunkCount() {
        Chunk chunk = makeChunk();
        float[] v = {0.1f};
        chunk.assignEmbeddingProvenance("nomic-embed-text", 1, Instant.now());
        EmbeddedChunk embedded = new EmbeddedChunk(chunk, v);

        when(chunkingService.chunk(any(), any(), any())).thenReturn(List.of(chunk));
        when(embeddingService.embed(anyList())).thenReturn(List.of(embedded));
        when(chunkApplicationService.saveAll(anyList())).thenReturn(List.of(chunk));
        when(embeddingProvider.modelName()).thenReturn("nomic-embed-text");
        when(embeddingProvider.dimension()).thenReturn(1);

        BenchmarkReport report = benchmarkService.benchmark(parsed("some text"), documentId, tenantId);

        assertThat(report.chunkCount()).isEqualTo(1);
        assertThat(report.embeddingModel()).isEqualTo("nomic-embed-text");
        assertThat(report.embeddingDimension()).isEqualTo(1);
    }

    @Test
    void benchmark_timingsAreNonNegative() {
        Chunk chunk = makeChunk();
        chunk.assignEmbeddingProvenance("model", 2, Instant.now());

        when(chunkingService.chunk(any(), any(), any())).thenReturn(List.of(chunk));
        when(embeddingService.embed(anyList())).thenReturn(List.of(new EmbeddedChunk(chunk, new float[]{0.0f})));
        when(chunkApplicationService.saveAll(anyList())).thenReturn(List.of(chunk));
        when(embeddingProvider.modelName()).thenReturn("model");
        when(embeddingProvider.dimension()).thenReturn(2);

        BenchmarkReport report = benchmarkService.benchmark(parsed("text"), documentId, tenantId);

        assertThat(report.chunkTime().isNegative()).isFalse();
        assertThat(report.embedTime().isNegative()).isFalse();
        assertThat(report.persistTime().isNegative()).isFalse();
        assertThat(report.totalTime().isNegative()).isFalse();
    }

    @Test
    void benchmark_summaryContainsModelAndDimension() {
        Chunk chunk = makeChunk();
        chunk.assignEmbeddingProvenance("nomic-embed-text", 768, Instant.now());

        when(chunkingService.chunk(any(), any(), any())).thenReturn(List.of(chunk));
        when(embeddingService.embed(anyList())).thenReturn(List.of(new EmbeddedChunk(chunk, new float[768])));
        when(chunkApplicationService.saveAll(anyList())).thenReturn(List.of(chunk));
        when(embeddingProvider.modelName()).thenReturn("nomic-embed-text");
        when(embeddingProvider.dimension()).thenReturn(768);

        BenchmarkReport report = benchmarkService.benchmark(parsed("content"), documentId, tenantId);

        assertThat(report.summary()).contains("nomic-embed-text").contains("768");
    }
}
