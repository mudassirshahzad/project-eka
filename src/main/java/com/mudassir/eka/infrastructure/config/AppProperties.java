package com.mudassir.eka.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Ingestion    ingestion,
        Retrieval    retrieval,
        Conversation conversation,
        Storage      storage
) {

    public record Ingestion(
            int chunkSize,
            int chunkOverlap,
            int embeddingBatchSize
    ) {}

    public record Retrieval(
            int    topKCandidates,
            int    topKResults,
            double hybridAlpha
    ) {}

    public record Conversation(
            int memoryWindowSize
    ) {}

    public record Storage(
            String documentRoot
    ) {}
}
