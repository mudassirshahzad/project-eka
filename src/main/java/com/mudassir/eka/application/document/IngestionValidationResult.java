package com.mudassir.eka.application.document;

import java.util.List;

public record IngestionValidationResult(
        boolean      valid,
        int          expectedCount,
        int          indexedCount,
        List<String> violations
) {

    public static IngestionValidationResult ok(int count) {
        return new IngestionValidationResult(true, count, count, List.of());
    }
}
