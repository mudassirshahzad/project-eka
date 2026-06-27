package com.mudassir.eka.application.document;

import java.time.Duration;

public record BenchmarkReport(
        int      chunkCount,
        Duration parseTime,
        Duration chunkTime,
        Duration embedTime,
        Duration indexTime,
        Duration persistTime,
        Duration totalTime,
        String   embeddingModel,
        int      embeddingDimension
) {

    public double chunksPerSecond() {
        long ms = totalTime.toMillis();
        return ms > 0 ? (chunkCount * 1000.0) / ms : 0.0;
    }

    public double vectorsPerSecond() {
        long ms = indexTime.toMillis();
        return ms > 0 ? (chunkCount * 1000.0) / ms : 0.0;
    }

    public String summary() {
        return ("IngestionBenchmark{chunks=%d, parse=%dms, chunk=%dms, embed=%dms, " +
                "index=%dms, persist=%dms, total=%dms, " +
                "throughput=%.1f chunks/s, vectors=%.1f/s, model=%s, dim=%d}")
                .formatted(
                        chunkCount,
                        parseTime.toMillis(),
                        chunkTime.toMillis(),
                        embedTime.toMillis(),
                        indexTime.toMillis(),
                        persistTime.toMillis(),
                        totalTime.toMillis(),
                        chunksPerSecond(),
                        vectorsPerSecond(),
                        embeddingModel,
                        embeddingDimension
                );
    }
}
