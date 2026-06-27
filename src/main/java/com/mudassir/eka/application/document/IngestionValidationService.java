package com.mudassir.eka.application.document;

import com.mudassir.eka.domain.chunk.Chunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class IngestionValidationService {

    public IngestionValidationResult validate(List<Chunk> chunks, int expectedCount) {
        List<String> violations = new ArrayList<>();

        if (chunks.size() != expectedCount) {
            violations.add("Expected %d chunk(s) but got %d".formatted(expectedCount, chunks.size()));
        }

        long nullVectorCount = chunks.stream()
                .filter(c -> c.getVectorId() == null)
                .count();
        if (nullVectorCount > 0) {
            violations.add("%d chunk(s) have null vectorId — Weaviate indexing incomplete".formatted(nullVectorCount));
        }

        long nonNullCount = chunks.size() - nullVectorCount;
        long distinctCount = chunks.stream()
                .filter(c -> c.getVectorId() != null)
                .map(Chunk::getVectorId)
                .distinct()
                .count();
        if (distinctCount != nonNullCount) {
            violations.add("Duplicate vectorIds detected: %d distinct out of %d indexed".formatted(distinctCount, nonNullCount));
        }

        long missingProvenance = chunks.stream()
                .filter(c -> !c.isEmbedded())
                .count();
        if (missingProvenance > 0) {
            violations.add("%d chunk(s) missing embedding provenance (embeddingModel is null)".formatted(missingProvenance));
        }

        boolean valid = violations.isEmpty();
        IngestionValidationResult result = new IngestionValidationResult(
                valid,
                expectedCount,
                (int) nonNullCount,
                List.copyOf(violations)
        );

        if (valid) {
            log.debug("Ingestion validation passed: {} chunks, {} vectors", expectedCount, nonNullCount);
        } else {
            log.warn("Ingestion validation FAILED with {} violation(s): {}", violations.size(), violations);
        }
        return result;
    }
}
