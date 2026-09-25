package com.mudassirshahzad.eka.application.evaluation;

import java.util.List;
import java.util.Set;

/**
 * Standard information-retrieval metrics over a ranked result list (WP-4, ADR RQ04).
 *
 * <p>Lives in {@code application} rather than in test code deliberately: these are the definitions
 * any future benchmark — including one run against a real, human-labelled dataset — must share with
 * the synthetic one, so that a later number is comparable to today's. Putting them in a test source
 * set would guarantee a second, subtly different implementation the first time someone benchmarks
 * outside the test suite.
 *
 * <p>All methods are pure and side-effect free; a relevance judgement is a set of ids, so the
 * metrics are independent of how relevance was decided (synthetic labels today, human labels later).
 */
public final class RetrievalMetrics {

    private RetrievalMetrics() {}

    /**
     * Fraction of the known-relevant items that appear in the top {@code k}.
     *
     * <p>Answers "did we find them at all" and is deliberately insensitive to ordering — it is the
     * metric that catches a retriever dropping relevant content entirely, which re-ranking cannot
     * fix because re-ranking only reorders what retrieval already returned.
     */
    public static double recallAtK(List<String> rankedIds, Set<String> relevantIds, int k) {
        if (relevantIds.isEmpty()) return 0.0;
        long found = rankedIds.stream().limit(k).filter(relevantIds::contains).count();
        return (double) found / relevantIds.size();
    }

    /**
     * Reciprocal of the rank of the first relevant item, or 0 if none appears in the top {@code k}.
     *
     * <p>Answers "how far must a reader scroll before the first useful result" — the metric that
     * moves most when re-ranking works, since it rewards promoting one good answer to the top.
     */
    public static double reciprocalRankAtK(List<String> rankedIds, Set<String> relevantIds, int k) {
        for (int i = 0; i < rankedIds.size() && i < k; i++) {
            if (relevantIds.contains(rankedIds.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    /**
     * Normalised discounted cumulative gain at {@code k}, with binary relevance.
     *
     * <p>Unlike reciprocal rank it accounts for <em>every</em> relevant item's position, not just
     * the first, and unlike recall it is order-sensitive. Normalising against the ideal ordering
     * keeps it comparable across queries that have different numbers of relevant documents — without
     * that, a query with three relevant passages would dominate one with a single passage.
     */
    public static double ndcgAtK(List<String> rankedIds, Set<String> relevantIds, int k) {
        if (relevantIds.isEmpty()) return 0.0;

        double dcg = 0.0;
        for (int i = 0; i < rankedIds.size() && i < k; i++) {
            if (relevantIds.contains(rankedIds.get(i))) {
                dcg += 1.0 / log2(i + 2.0);
            }
        }

        double idealDcg = 0.0;
        int idealHits = Math.min(relevantIds.size(), k);
        for (int i = 0; i < idealHits; i++) {
            idealDcg += 1.0 / log2(i + 2.0);
        }

        return idealDcg == 0.0 ? 0.0 : dcg / idealDcg;
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }
}
