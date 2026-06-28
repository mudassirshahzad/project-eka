package com.mudassir.eka.infrastructure.retrieval.postgres;

/**
 * Normalizes raw PostgreSQL {@code ts_rank} scores to {@code [0.0, 1.0]}.
 *
 * <h3>Algorithm — max-normalization</h3>
 * <p>For a result set with raw scores {@code [s₀, s₁, …, sₙ₋₁]}:
 * <pre>
 *   normalized_i = s_i / max(s)     if max(s) &gt; 0
 *   normalized_i = 0.0              if max(s) &le; 0
 * </pre>
 * This maps the highest-scoring result to {@code 1.0} and all others
 * proportionally below it, preserving relative ordering within the result set.
 *
 * <h3>Why max-normalization, not min-max?</h3>
 * <p>Min-max stretches scores so that the lowest result is always {@code 0.0}.
 * That inflates perceived differences among low-quality matches and forces the
 * worst result out regardless of its absolute quality. Max-normalization avoids
 * this: a result that barely matches still gets a low score (close to zero),
 * and the {@code minimumScore} gate then removes it naturally.
 *
 * <h3>BM25 score boundedness</h3>
 * <p>{@code ts_rank} scores are theoretically unbounded, though they rarely
 * exceed {@code 1.0} in practice with the default normalization option. This
 * class treats them as unbounded (divides by the empirical maximum) rather than
 * assuming they are already in {@code [0, 1]} — see
 * {@link com.mudassir.eka.infrastructure.retrieval.weaviate.RetrievedChunkMapper#clampToUnitRange}
 * for the contrast with the Weaviate adapter's clamp-only approach.
 */
class Bm25ScoreNormalizer {

    private Bm25ScoreNormalizer() {}

    /**
     * Returns a parallel array of max-normalized scores in {@code [0.0, 1.0]}.
     * The input array is not modified. An empty input returns an empty array.
     */
    static double[] normalize(double[] rawScores) {
        if (rawScores.length == 0) {
            return new double[0];
        }

        double max = 0.0;
        for (double s : rawScores) {
            if (s > max) max = s;
        }

        if (max <= 0.0) {
            return new double[rawScores.length]; // all zeros
        }

        double[] normalized = new double[rawScores.length];
        for (int i = 0; i < rawScores.length; i++) {
            normalized[i] = Math.max(0.0, Math.min(1.0, rawScores[i] / max));
        }
        return normalized;
    }
}
