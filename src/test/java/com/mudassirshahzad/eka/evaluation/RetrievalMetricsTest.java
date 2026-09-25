package com.mudassirshahzad.eka.evaluation;

import com.mudassirshahzad.eka.application.evaluation.RetrievalMetrics;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for the metric definitions themselves (WP-4, ADR RQ04).
 *
 * <p>These are deliberately checked against hand-computed values rather than a library: the whole
 * point of the evaluation harness is that a future benchmark against real labelled data is
 * comparable to today's synthetic one, which only holds if the metrics are provably the textbook
 * definitions and not an approximation that drifted.
 */
class RetrievalMetricsTest {

    private final Set<String> relevant = Set.of("a", "b");

    @Test
    void recallAtK_countsRelevantItemsFoundRegardlessOfOrder() {
        assertThat(RetrievalMetrics.recallAtK(List.of("a", "x", "b"), relevant, 3)).isEqualTo(1.0);
        assertThat(RetrievalMetrics.recallAtK(List.of("b", "a"), relevant, 3)).isEqualTo(1.0);
        assertThat(RetrievalMetrics.recallAtK(List.of("a", "x", "y"), relevant, 3)).isEqualTo(0.5);
        assertThat(RetrievalMetrics.recallAtK(List.of("x", "y"), relevant, 3)).isEqualTo(0.0);
    }

    @Test
    void recallAtK_respectsTheCutoff() {
        // "b" sits at position 3, outside k=2, so only half the relevant set counts as found.
        assertThat(RetrievalMetrics.recallAtK(List.of("a", "x", "b"), relevant, 2)).isEqualTo(0.5);
    }

    @Test
    void reciprocalRank_rewardsTheFirstRelevantPosition() {
        assertThat(RetrievalMetrics.reciprocalRankAtK(List.of("a", "x"), relevant, 5)).isEqualTo(1.0);
        assertThat(RetrievalMetrics.reciprocalRankAtK(List.of("x", "a"), relevant, 5)).isEqualTo(0.5);
        assertThat(RetrievalMetrics.reciprocalRankAtK(List.of("x", "y", "b"), relevant, 5))
                .isCloseTo(1.0 / 3.0, within(1e-9));
    }

    @Test
    void reciprocalRank_isZeroWhenNothingRelevantIsWithinTheCutoff() {
        assertThat(RetrievalMetrics.reciprocalRankAtK(List.of("x", "y", "a"), relevant, 2)).isEqualTo(0.0);
        assertThat(RetrievalMetrics.reciprocalRankAtK(List.of("x", "y"), relevant, 5)).isEqualTo(0.0);
    }

    @Test
    void ndcg_isOneForTheIdealOrdering_andLowerWhenRelevantItemsSitDeeper() {
        assertThat(RetrievalMetrics.ndcgAtK(List.of("a", "b", "x"), relevant, 3)).isEqualTo(1.0);

        // Relevant items at positions 2 and 3: DCG = 1/log2(3) + 1/log2(4);
        // ideal DCG = 1/log2(2) + 1/log2(3).
        double dcg      = 1.0 / (Math.log(3) / Math.log(2)) + 1.0 / 2.0;
        double idealDcg = 1.0 + 1.0 / (Math.log(3) / Math.log(2));
        assertThat(RetrievalMetrics.ndcgAtK(List.of("x", "a", "b"), relevant, 3))
                .isCloseTo(dcg / idealDcg, within(1e-9));
    }

    @Test
    void ndcg_normalisesAcrossQueriesWithDifferentNumbersOfRelevantItems() {
        // A single relevant item ranked first scores 1.0, exactly like two relevant items ranked
        // first and second — without normalisation the second query would dominate an average.
        assertThat(RetrievalMetrics.ndcgAtK(List.of("a", "x"), Set.of("a"), 5)).isEqualTo(1.0);
        assertThat(RetrievalMetrics.ndcgAtK(List.of("a", "b"), Set.of("a", "b"), 5)).isEqualTo(1.0);
    }

    @Test
    void allMetricsAreZeroWhenNothingIsLabelledRelevant() {
        assertThat(RetrievalMetrics.recallAtK(List.of("a"), Set.of(), 5)).isEqualTo(0.0);
        assertThat(RetrievalMetrics.ndcgAtK(List.of("a"), Set.of(), 5)).isEqualTo(0.0);
        assertThat(RetrievalMetrics.reciprocalRankAtK(List.of("a"), Set.of(), 5)).isEqualTo(0.0);
    }
}
