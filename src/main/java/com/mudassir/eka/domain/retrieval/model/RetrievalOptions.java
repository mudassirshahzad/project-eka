package com.mudassir.eka.domain.retrieval.model;

public record RetrievalOptions(int topK, double minimumScore) {

    public static final int    DEFAULT_TOP_K         = 10;
    public static final double DEFAULT_MINIMUM_SCORE = 0.0;
    public static final int    MAX_TOP_K             = 100;

    public static final RetrievalOptions DEFAULT =
            new RetrievalOptions(DEFAULT_TOP_K, DEFAULT_MINIMUM_SCORE);

    public RetrievalOptions {
        if (topK < 1 || topK > MAX_TOP_K) {
            throw new IllegalArgumentException(
                    "topK must be between 1 and " + MAX_TOP_K + " but was " + topK);
        }
        if (minimumScore < 0.0 || minimumScore > 1.0) {
            throw new IllegalArgumentException(
                    "minimumScore must be between 0.0 and 1.0 but was " + minimumScore);
        }
    }

    public static RetrievalOptions of(int topK) {
        return new RetrievalOptions(topK, DEFAULT_MINIMUM_SCORE);
    }

    public static RetrievalOptions of(int topK, double minimumScore) {
        return new RetrievalOptions(topK, minimumScore);
    }
}
