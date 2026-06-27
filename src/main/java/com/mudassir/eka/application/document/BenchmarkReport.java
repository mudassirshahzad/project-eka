package com.mudassir.eka.application.document;

import java.time.Duration;

public record BenchmarkReport(
        int      chunkCount,
        Duration parseTime,
        Duration chunkTime,
        Duration embedTime,
        Duration persistTime,
        Duration totalTime,
        String   embeddingModel,
        int      embeddingDimension
) {

    public String summary() {
        return ("IngestionBenchmark{chunks=%d, parse=%dms, chunk=%dms, embed=%dms, " +
                "persist=%dms, total=%dms, model=%s, dim=%d}")
                .formatted(
                        chunkCount,
                        parseTime.toMillis(),
                        chunkTime.toMillis(),
                        embedTime.toMillis(),
                        persistTime.toMillis(),
                        totalTime.toMillis(),
                        embeddingModel,
                        embeddingDimension
                );
    }
}
