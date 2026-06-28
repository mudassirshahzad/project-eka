package com.mudassir.eka.infrastructure.retrieval.postgres;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class Bm25ScoreNormalizerTest {

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    void normalize_returnsEmptyArrayForEmptyInput() {
        double[] result = Bm25ScoreNormalizer.normalize(new double[0]);
        assertThat(result).isEmpty();
    }

    @Test
    void normalize_normalizesSingleNonZeroScoreToOne() {
        double[] result = Bm25ScoreNormalizer.normalize(new double[]{0.42});
        assertThat(result).hasSize(1);
        assertThat(result[0]).isEqualTo(1.0);
    }

    @Test
    void normalize_returnsAllZerosWhenAllScoresAreZero() {
        double[] result = Bm25ScoreNormalizer.normalize(new double[]{0.0, 0.0, 0.0});
        assertThat(result).containsExactly(0.0, 0.0, 0.0);
    }

    @Test
    void normalize_returnsAllZerosWhenMaxScoreIsNegative() {
        double[] result = Bm25ScoreNormalizer.normalize(new double[]{-0.1, -0.5});
        assertThat(result).containsExactly(0.0, 0.0);
    }

    // ── Normalization correctness ─────────────────────────────────────────────

    @Test
    void normalize_mapsHighestScoreToOne() {
        double[] result = Bm25ScoreNormalizer.normalize(new double[]{0.2, 0.8, 0.5});
        assertThat(result[1]).isEqualTo(1.0);
    }

    @Test
    void normalize_scoresAreProportionalToMaximum() {
        double[] result = Bm25ScoreNormalizer.normalize(new double[]{0.4, 0.8});
        assertThat(result[0]).isCloseTo(0.5, within(1e-9));
        assertThat(result[1]).isEqualTo(1.0);
    }

    @Test
    void normalize_preservesRelativeOrdering() {
        double[] raw = {0.1, 0.6, 0.3};
        double[] result = Bm25ScoreNormalizer.normalize(raw);
        assertThat(result[0]).isLessThan(result[2]);
        assertThat(result[2]).isLessThan(result[1]);
    }

    @Test
    void normalize_allEqualNonZeroScoresProduceAllOnes() {
        double[] result = Bm25ScoreNormalizer.normalize(new double[]{0.5, 0.5, 0.5});
        assertThat(result).containsExactly(1.0, 1.0, 1.0);
    }

    @Test
    void normalize_doesNotModifyInputArray() {
        double[] input = {0.3, 0.9};
        Bm25ScoreNormalizer.normalize(input);
        assertThat(input).containsExactly(0.3, 0.9);
    }

    // ── Defensive clamping ────────────────────────────────────────────────────

    @Test
    void normalize_clampsNormalizedScoreToOneWhenScoreExceedsMax() {
        // Raw scores where one is slightly above max due to floating-point drift
        double max = 0.5;
        double[] result = Bm25ScoreNormalizer.normalize(new double[]{max, max + 1e-15});
        assertThat(result[0]).isLessThanOrEqualTo(1.0);
        assertThat(result[1]).isLessThanOrEqualTo(1.0);
    }

    @Test
    void normalize_clampsNegativeMixedScoresToZero() {
        double[] result = Bm25ScoreNormalizer.normalize(new double[]{-0.1, 0.5, 0.8});
        assertThat(result[0]).isEqualTo(0.0);
        assertThat(result[1]).isGreaterThan(0.0);
        assertThat(result[2]).isEqualTo(1.0);
    }

    // ── Unit range guarantee ──────────────────────────────────────────────────

    @Test
    void normalize_allResultsAreWithinUnitRange() {
        double[] result = Bm25ScoreNormalizer.normalize(new double[]{0.01, 0.55, 0.23, 0.99, 1.05});
        for (double v : result) {
            assertThat(v).isBetween(0.0, 1.0);
        }
    }
}
